#include "dw3000.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <map>
#include <vector>
#include "SPI.h"
#include <esp_wifi.h>
#include <string.h>

#define PIN_RST 27  
#define PIN_IRQ 34  
#define PIN_SS 4    

#define RNG_DELAY_MS 50
#define TX_ANT_DLY 16385
#define RX_ANT_DLY 16385

#define ALL_MSG_COMMON_LEN 10
#define ALL_MSG_SN_IDX 2
#define POLL_MSG_LEN 25
#define POLL_MSG_ACTION_IDX 10 
#define POLL_MSG_ANCHOR_MAC_IDX 11 
#define POLL_MSG_TAG_MAC_IDX 17 

#define RESP_FRAME_LEN_WITH_CRC 28 
#define RESP_MSG_LEN 28 
#define RESP_MSG_TS_LEN 5           
#define RESP_MSG_POLL_RX_TS_IDX 10  
#define RESP_MSG_RESP_TX_TS_IDX 15  
#define RESP_MSG_ANCHOR_MAC_IDX 20  

#define POLL_TX_TO_RESP_RX_DLY_UUS 0 
#define RESP_RX_TIMEOUT_UUS 10000 
#define SPEED_OF_LIGHT 299792458.0f 
#define DWT_TIME_UNITS (1.0/499.2e6/128.0) 

#define TAG_MAC_PAYLOAD_LEN 6 
#define TAG_MAC_PAYLOAD_IDX (ALL_MSG_COMMON_LEN + 2)
#define MSG_SCAN_RESPONSE_LEN 20 
#define SCAN_MSG_LEN 12 

#ifndef SYS_STATUS_RXPHE
#define SYS_STATUS_RXPHE 0x00001000UL
#endif
#ifndef SYS_STATUS_RXFCE
#define SYS_STATUS_RXFCE 0x00008000UL
#endif

static dwt_config_t config = {
    5, DWT_PLEN_128, DWT_PAC8, 9, 9, 2, DWT_BR_6M8, DWT_PHRMODE_STD, DWT_PHRRATE_STD, 
    (129 + 8 - 8), DWT_STS_MODE_OFF, DWT_STS_LEN_64, DWT_PDOA_M0
};

// --- Synchronization & Drift Management ---
unsigned long local_baseline_millis = 0;
uint64_t server_baseline_ms = 0;
uint32_t current_round_id = 0; // NUMERIC ID

const int64_t ACCEPTANCE_INTERVAL_MS = 20;

const char* WIFI_SSID = "XDD";
const char* WIFI_PASSWORD = "wifinosquartos1234";
const char* SERVER_URL_BASE = "http://192.168.0.105:8080/C03a";

const char* PATH_BOOT = "/anchorRegistration";
const char* PATH_MEASURE = "/measurementReport";
const char* PATH_SCAN = "/scanReport";

static uint8_t tx_poll_msg[] = { 
    0x41, 0x88, 0, 0xCA, 0xDE, 'W', 'A', 'V', 'E', 0xE0, 
    0x01, 0,0,0,0,0,0, 0,0,0,0,0,0, 0, 0 
};
static uint8_t tx_scan_msg[] = { 0x41, 0x88, 0, 0xFF, 0xFF, 'S', 'C', 'A', 'N', 0x00, 0, 0 };
static uint8_t rx_buffer[30]; 

uint8_t ANCHOR_MAC[6];
String ANCHOR_ID_STRING; 

struct ScheduledTag {
    String tagID;
    uint64_t targetTime;
};

std::vector<ScheduledTag> scheduled_tasks;
uint8_t frame_seq_nb = 0;

String current_action_name = "scan";
unsigned long action_duration_ms = 500; 
uint64_t g_next_action_timestamp_ms = 0; 

extern SPISettings _fastSPI;
extern dwt_txconfig_t txconfig_options;

struct MeasurementResult {
    String tagID;
    double distance;
    uint64_t timestamp;
};

extern "C" void UART_puts(const char *s) { Serial.print(s); }

void recover_from_stall() {
    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_TX | SYS_STATUS_ALL_RX_ERR | SYS_STATUS_ALL_RX_TO | SYS_STATUS_RXFCG_BIT_MASK);
    delay(2);
}

extern "C" void resp_msg_get_ts(const uint8_t *ts_field, uint32_t *ts) {
    *ts = 0;
    for (int i = 0; i < RESP_MSG_TS_LEN; i++) {
        *ts |= ((uint32_t)ts_field[i]) << (i * 8);
    }
}

void resp_msg_get_ts_40bit(const uint8_t *ts_field, uint64_t *ts) {
    *ts = 0;
    for (int i = 0; i < RESP_MSG_TS_LEN; i++) {
        *ts |= ((uint64_t)ts_field[i]) << (i * 8);
    }
}

uint64_t get_timestamp_u64(uint8_t *ts_buf) {
    uint64_t ts = 0;
    for (int i = 0; i < 5; i++) {
        ts |= ((uint64_t)ts_buf[i]) << (i * 8);
    }
    return ts;
}

uint64_t get_current_server_time_ms() {
    if (server_baseline_ms == 0) return 0;
    return server_baseline_ms + (millis() - local_baseline_millis);
}

bool vector_contains(const std::vector<String>& vec, const String& val) {
    for (const String& s : vec) { if (s == val) return true; }
    return false;
}

bool mac_string_to_bytes(const String& mac_str, uint8_t mac_bytes[6]) {
    unsigned int values[6];
    int result = sscanf(mac_str.c_str(), "%x:%x:%x:%x:%x:%x", 
                        &values[0], &values[1], &values[2], &values[3], &values[4], &values[5]);
    if (result != 6) return false;
    for (int i = 0; i < 6; i++) mac_bytes[i] = (uint8_t)values[i];
    return true;
}

void readMacAddress() { 
    esp_err_t ret = esp_wifi_get_mac(WIFI_IF_STA, ANCHOR_MAC);
    if (ret != ESP_OK) { Serial.println("FATAL: Mac read failed."); while(1); }
}

String mac_to_string(const uint8_t mac_addr[]) {
    char buf[18];
    sprintf(buf, "%02X:%02X:%02X:%02X:%02X:%02X", 
    mac_addr[0], mac_addr[1], mac_addr[2], mac_addr[3], mac_addr[4], mac_addr[5]);
    return String(buf);
}

void resetToRegistration() {
    Serial.println("RECOVERY: Resetting state to Registration...");
    scheduled_tasks.clear();
    current_action_name = "register";
    g_next_action_timestamp_ms = 0;
    int retries = 0;
    while (!report_boot()) { 
        Serial.printf("Reg Retry %d...\n", ++retries);
        delay(5000);
    }
    Serial.println("Re-registered. Awaiting next command.");
}

void parse_server_response(String payload) {
    StaticJsonDocument<1024> doc;
    DeserializationError error = deserializeJson(doc, payload);
    
    if (error) {
        Serial.printf("CRITICAL JSON Parse Error: %s\n", error.c_str());
        Serial.println(payload);
        return;
    }

    if (!doc.containsKey("serverTimeNow") && !doc.containsKey("error")) {
        Serial.println("CRITICAL ERROR: JSON payload is missing 'serverTimeNow'!");
        Serial.println(payload);
        return;
    }

    local_baseline_millis = millis();
    server_baseline_ms = doc["serverTimeNow"].as<uint64_t>();

    if (doc.containsKey("actionToExecute")) {
        current_action_name = doc["actionToExecute"].as<String>();
        if (current_action_name == "register") {
            resetToRegistration();
            return;
        }

        if (current_action_name.indexOf("can") != -1) { 
            g_next_action_timestamp_ms = doc["whenToExecute"].as<uint64_t>();
            action_duration_ms = 500; 
        } 
        else if (current_action_name == "measure") {
            current_round_id = doc["roundId"].as<uint32_t>(); 
            scheduled_tasks.clear();
            JsonArray tags = doc["tags"].as<JsonArray>();
            if (tags && tags.size() > 0) {
                for (JsonObject tag : tags) {
                    ScheduledTag task;
                    task.tagID = tag["deviceID"].as<String>();
                    task.targetTime = tag["whenToExecute"].as<uint64_t>();
                    scheduled_tasks.push_back(task);
                }
                // Update global target time to the first task's target
                g_next_action_timestamp_ms = scheduled_tasks[0].targetTime;
            }
        }
    }
}

bool post_data_to_server(const String& path, const String& json_payload) {
    if (WiFi.status() != WL_CONNECTED) return false;
    HTTPClient http;
    http.begin(String(SERVER_URL_BASE) + path);
    http.addHeader("Content-Type", "application/json");
    int httpResponseCode = http.POST(json_payload);
    if (httpResponseCode == HTTP_CODE_OK) {
        parse_server_response(http.getString());
    } else {
        Serial.printf("HTTP Failed: %d\n", httpResponseCode);
    }
    http.end();
    return (httpResponseCode == HTTP_CODE_OK);
}

void report_consolidated_measurements(const std::vector<MeasurementResult>& results, unsigned long duration) {
    StaticJsonDocument<1024> doc; 
    doc["anchorID"] = ANCHOR_ID_STRING;
    doc["actualDurationMs"] = duration; 
    doc["roundId"] = current_round_id; 
    
    JsonArray tagArray = doc.createNestedArray("tags");
    for (const auto& result : results) {
        JsonObject measurement = tagArray.createNestedObject();
        measurement["tagID"] = result.tagID;
        measurement["distance"] = result.distance;
        measurement["executedAt"] = result.timestamp; 
    }
    
    String json_payload;
    serializeJson(doc, json_payload);
    post_data_to_server(PATH_MEASURE, json_payload);
}

bool report_boot() {
    StaticJsonDocument<200> doc;
    doc["anchorID"] = ANCHOR_ID_STRING;
    String json_payload;
    serializeJson(doc, json_payload);
    return post_data_to_server(PATH_BOOT, json_payload);
}

void report_scan_data(const std::vector<String>& discovered_tags) {
    StaticJsonDocument<500> doc;
    doc["anchorID"] = ANCHOR_ID_STRING;
    JsonArray tagArray = doc.createNestedArray("tags");
    for (const String& tagID : discovered_tags) {
        JsonObject tagObject = tagArray.createNestedObject();
        tagObject["tagID"] = tagID; 
    }
    String json_payload;
    serializeJson(doc, json_payload);
    post_data_to_server(PATH_SCAN, json_payload);
}

void init_poll_msg(const uint8_t *anchor_mac) {
    tx_poll_msg[POLL_MSG_ACTION_IDX] = 0x01; 
    memcpy(&tx_poll_msg[POLL_MSG_ANCHOR_MAC_IDX], anchor_mac, 6);
}

void connect_to_wifi() {
    Serial.print("Connecting to WiFi");
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); yield(); }
    Serial.println(" Connected.");
}

void perform_scan_sequence() {
    Serial.println("--- SCHEDULED SCANNING ---");
    std::vector<String> tags_seen_this_scan;
    bool tx_success = false;

    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK | 0x00000010UL); 
    dwt_write32bitreg(SYS_CFG_ID, dwt_read32bitreg(SYS_CFG_ID) & ~0x00000100UL);
    delay(2); 

    tx_scan_msg[ALL_MSG_SN_IDX] = frame_seq_nb++;
    dwt_writetxdata(SCAN_MSG_LEN, tx_scan_msg, 0);
    dwt_writetxfctrl(SCAN_MSG_LEN, 0, 1);
    if (dwt_starttx(DWT_START_TX_IMMEDIATE) == DWT_SUCCESS) {
        unsigned long tx_start = millis();
        while (millis() - tx_start < 50) {
            if (dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK) {
                tx_success = true;
                break;
            }
            yield();
        }
        if (!tx_success) {
            Serial.println("Err: Scan TX Stalled");
            dwt_write32bitreg(SYS_STATUS_ID, 0xFFFFFFFF);
        } 
    }

    if (tx_success) {
        dwt_setrxtimeout(10000); 
        dwt_rxenable(DWT_START_RX_IMMEDIATE);
        
        unsigned long scan_start = millis();
        uint32_t status_reg;
        
        while (millis() - scan_start < action_duration_ms) {
            while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR))) {
                if (millis() - scan_start > action_duration_ms) break;
                yield();
            }

            if (millis() - scan_start >= action_duration_ms) break;
            if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
                dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
                uint32_t len = dwt_read32bitreg(RX_FINFO_ID) & RXFLEN_MASK;
                
                if (len == MSG_SCAN_RESPONSE_LEN) {
                    dwt_readrxdata(rx_buffer, len, 0);
                    uint8_t tag_mac[TAG_MAC_PAYLOAD_LEN];
                    memcpy(tag_mac, &rx_buffer[TAG_MAC_PAYLOAD_IDX], TAG_MAC_PAYLOAD_LEN);
                    
                    String tagID = mac_to_string(tag_mac);
                    if (!vector_contains(tags_seen_this_scan, tagID)) {
                        Serial.print("Discovered Tag: ");
                        Serial.println(tagID);
                        tags_seen_this_scan.push_back(tagID);
                    }
                }
                dwt_rxenable(DWT_START_RX_IMMEDIATE);
            } else {
                dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);
                dwt_rxenable(DWT_START_RX_IMMEDIATE);
            }
        }
    }
    
    report_scan_data(tags_seen_this_scan);
}

void perform_ranging_sequence() {
    Serial.println("--- RELATIVE STAGGERED RANGING ---");
    std::vector<MeasurementResult> measurements;
    unsigned long round_start_time = millis(); 

    dwt_write32bitreg(SYS_STATUS_ID, 0xFFFFFFFF);
    dwt_write32bitreg(SYS_CFG_ID, dwt_read32bitreg(SYS_CFG_ID) & ~0x00000100UL); 
    delay(2);
    
    for (const auto& task : scheduled_tasks) {
        int64_t time_to_wait_ms = task.targetTime - server_baseline_ms;
        unsigned long local_target_millis = local_baseline_millis + time_to_wait_ms;

        if (time_to_wait_ms < -ACCEPTANCE_INTERVAL_MS) {
            Serial.printf("Skipping %s: Late by %lld ms\n", task.tagID.c_str(), llabs(time_to_wait_ms));
            continue; 
        }

        if (millis() < local_target_millis && (local_target_millis - millis() > 20)) {
            delay(local_target_millis - millis() - 10);
        }
        while (millis() < local_target_millis) {
            yield();
        }
        
        uint8_t tx_msg[POLL_MSG_LEN];
        memcpy(tx_msg, tx_poll_msg, POLL_MSG_LEN);
        tx_msg[ALL_MSG_SN_IDX] = frame_seq_nb++; 
        
        uint8_t tag_bytes[6];
        if (!mac_string_to_bytes(task.tagID, tag_bytes)) continue;
        memcpy(&tx_msg[POLL_MSG_TAG_MAC_IDX], tag_bytes, 6);

        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK | 0x00000010UL);
        dwt_writetxdata(POLL_MSG_LEN, tx_msg, 0);
        dwt_writetxfctrl(POLL_MSG_LEN, 0, 1);

        if (dwt_starttx(DWT_START_TX_IMMEDIATE) != DWT_SUCCESS) continue;

        unsigned long tx_start = millis();
        while (millis() - tx_start < 50) {
            if (dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK) break;
            yield();
        }

        dwt_setrxtimeout(RESP_RX_TIMEOUT_UUS);
        dwt_rxenable(DWT_START_RX_IMMEDIATE);

        uint32_t status_reg = 0;
        unsigned long rx_start = millis();
        while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR))) {
            if (millis() - rx_start > 20) break;
            yield();
        }

        if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
            dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
            uint32_t len = dwt_read32bitreg(RX_FINFO_ID) & RXFLEN_MASK;
            
            if (len == RESP_FRAME_LEN_WITH_CRC) {
                dwt_readrxdata(rx_buffer, len, 0);
                if (memcmp(&rx_buffer[RESP_MSG_ANCHOR_MAC_IDX], ANCHOR_MAC, 6) == 0) {
                    
                    uint8_t buf_poll_tx[5], buf_resp_rx[5];
                    dwt_readtxtimestamp(buf_poll_tx);
                    dwt_readrxtimestamp(buf_resp_rx);
                    
                    uint64_t t_poll_tx = get_timestamp_u64(buf_poll_tx);
                    uint64_t t_resp_rx = get_timestamp_u64(buf_resp_rx);
                    
                    uint64_t t_poll_rx_tag, t_resp_tx_tag;
                    resp_msg_get_ts_40bit(&rx_buffer[RESP_MSG_POLL_RX_TS_IDX], &t_poll_rx_tag);
                    resp_msg_get_ts_40bit(&rx_buffer[RESP_MSG_RESP_TX_TS_IDX], &t_resp_tx_tag);
                    
                    float clockOffsetRatio = ((float)dwt_readclockoffset()) / (uint32_t)(1 << 26);
                    int64_t rtd_init = (int64_t)t_resp_rx - (int64_t)t_poll_tx;
                    int64_t rtd_resp = (int64_t)t_resp_tx_tag - (int64_t)t_poll_rx_tag;
                    if (rtd_init < 0) rtd_init += (1ULL << 40);
                    if (rtd_resp < 0) rtd_resp += (1ULL << 40);
                    double tof = ((rtd_init - rtd_resp * (1.0f - clockOffsetRatio)) / 2.0) * DWT_TIME_UNITS;
                    double dist = tof * SPEED_OF_LIGHT;
                    if (dist < 0) dist = 0.0;

                    uint64_t translated_server_time = server_baseline_ms + (millis() - local_baseline_millis);
                    measurements.push_back({task.tagID, dist, translated_server_time});
                    Serial.printf("RANGE SUCCESS: %s -> %.3f m\n", task.tagID.c_str(), dist);
                }
            }
        }
        dwt_write32bitreg(SYS_STATUS_ID, 0xFFFFFFFF);
        delay(2); 
    }
    
    // NOTE: This triggers post_data_to_server, which triggers parse_server_response, 
    // which UPDATES g_next_action_timestamp_ms with the new schedule from the server!
    unsigned long round_duration = millis() - round_start_time;
    report_consolidated_measurements(measurements, round_duration);
}

void setup() {
    Serial.begin(115200);
    Serial.println("\n--- Anchor Boot ---");
    
    WiFi.mode(WIFI_STA);
    readMacAddress();
    ANCHOR_ID_STRING = mac_to_string(ANCHOR_MAC);
    connect_to_wifi();
    
    int retries = 0;
    while (!report_boot()) { 
        Serial.printf("Reg Retry %d...\n", ++retries); delay(5000);
    }

    _fastSPI = SPISettings(8000000L, MSBFIRST, SPI_MODE0); 
    spiBegin(PIN_IRQ, PIN_RST);
    spiSelect(PIN_SS);
    delay(2);
    while (!dwt_checkidlerc()) { recover_from_stall(); delay(10); } 
    if (dwt_initialise(DWT_DW_INIT) == DWT_ERROR) { UART_puts("INIT FAILED\r\n"); while(1); }

    dwt_setleds(DWT_LEDS_ENABLE | DWT_LEDS_INIT_BLINK);
    if (dwt_configure(&config)) { UART_puts("CONFIG FAILED\r\n"); while(1); }
    dwt_configuretxrf(&txconfig_options);

    dwt_setrxantennadelay(RX_ANT_DLY);
    dwt_settxantennadelay(TX_ANT_DLY);
    
    dwt_setrxaftertxdelay(POLL_TX_TO_RESP_RX_DLY_UUS); 
    dwt_setrxtimeout(RESP_RX_TIMEOUT_UUS);
    dwt_setlnapamode(DWT_LNA_ENABLE | DWT_PA_ENABLE);

    init_poll_msg(ANCHOR_MAC);
    Serial.println("Setup Complete.");
}

void loop() {
    uint64_t now_server = get_current_server_time_ms();

    if (now_server == 0) { delay(100); return; }

    if (g_next_action_timestamp_ms != 0) {
        if (now_server >= g_next_action_timestamp_ms) {
            
            // FIX: Capture the timestamp we are executing right now
            uint64_t executed_target = g_next_action_timestamp_ms;
            
            if (now_server <= g_next_action_timestamp_ms + ACCEPTANCE_INTERVAL_MS) {
                Serial.printf("Executing %s now...\n", current_action_name.c_str());
                if (current_action_name == "measure") perform_ranging_sequence();
                else perform_scan_sequence();
            } else {
                Serial.println("Action Expired - Window Missed.");
            }
            
            // FIX: Only clear the global timestamp if it hasn't been updated 
            // by a fresh server response during the execution/report phase!
            if (g_next_action_timestamp_ms == executed_target) {
                g_next_action_timestamp_ms = 0;
            }
        }
    } else {
        static unsigned long last_retry = 0;
        if (millis() - last_retry > 5000) {
            Serial.println("Idle: Requesting new schedule...");
            report_boot(); 
            last_retry = millis();
        }
    }
    delay(1);
}