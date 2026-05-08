#include "dw3000.h"
#include <WiFi.h>
#include <SPI.h>
#include <esp_wifi.h>
#include <string.h>

#define PIN_RST 27  
#define PIN_IRQ 34  
#define PIN_SS 4    

#define TX_ANT_DLY 16385
#define RX_ANT_DLY 16385

#define ALL_MSG_COMMON_LEN 10
#define ALL_MSG_SN_IDX 2
#define POLL_MSG_LEN 25
#define POLL_MSG_ACTION_IDX 10
#define POLL_MSG_ANCHOR_MAC_IDX 11
#define POLL_MSG_TAG_MAC_IDX 17 

#define RESP_MSG_LEN 28 
#define RESP_MSG_TS_LEN 5 
#define RESP_MSG_POLL_RX_TS_IDX 10 
#define RESP_MSG_RESP_TX_TS_IDX 15 
#define RESP_MSG_ANCHOR_MAC_IDX 20 

#define TAG_MAC_PAYLOAD_LEN 6
#define TAG_MAC_PAYLOAD_OFFSET_IN_TX_BUFFER (ALL_MSG_COMMON_LEN + 2)
#define MSG_SCAN_RESPONSE_LEN 20 
#define SCAN_MSG_LEN 12 

#define RX_TIMEOUT_UUS 500000 

// --- FIXED TIMING CONSTANTS ---
#define UUS_TO_DWT_TIME 63897 // Conversion factor for microseconds to DWT units
#define POLL_RX_TO_RESP_TX_DLY_UUS 900 // Fixed delay: Tag waits 900us before replying

#define DWT_TIME_UNITS (1.0/499.2e6/128.0)

// --- CONFIG: MODE 2 (Match Anchor) ---
static dwt_config_t config = {
    5,                /* Channel number. */
    DWT_PLEN_128,     /* Preamble length. */
    DWT_PAC8,         /* Preamble acquisition chunk size. */
    9,                /* TX preamble code. */
    9,                /* RX preamble code. */
    2,                /* Mode 2 */
    DWT_BR_6M8,       /* Data rate. */
    DWT_PHRMODE_STD,  /* PHY header mode. */
    DWT_PHRRATE_STD,  /* PHY header rate. */
    (129 + 8 - 8),    /* SFD timeout. */
    DWT_STS_MODE_OFF, /* STS disabled */
    DWT_STS_LEN_64,   /* STS length */
    DWT_PDOA_M0       /* PDOA mode off */
};

static uint8_t rx_buffer[30]; 

static uint8_t tx_resp_msg[RESP_MSG_LEN] = {
    0x41, 0x88, 0, 0xCA, 0xDE, 'V', 'E', 'W', 'A', 0xE1, 
    0,0,0,0,0, // Poll RX TS
    0,0,0,0,0, // Resp TX TS
    0,0,0,0,0,0, // Anchor MAC
    0, 0 // Padding
};

static uint8_t tx_scan_resp_msg[MSG_SCAN_RESPONSE_LEN];
uint8_t TAG_MAC[6];
String TAG_ID_STRING;

extern SPISettings _fastSPI;
extern dwt_txconfig_t txconfig_options;
extern "C" void UART_puts(const char *s) { Serial.print(s); }

String mac_to_string(const uint8_t mac_addr[]) {
    char buf[18];
    sprintf(buf, "%02X:%02X:%02X:%02X:%02X:%02X", 
            mac_addr[0], mac_addr[1], mac_addr[2], mac_addr[3], mac_addr[4], mac_addr[5]);
    return String(buf);
}

void readMacAddress() {
    esp_err_t ret = esp_wifi_get_mac(WIFI_IF_STA, TAG_MAC);
    if (ret != ESP_OK) { Serial.println("FATAL: Failed to read MAC."); while(1); }
}

uint64_t get_timestamp_u64(uint8_t *ts_buf) {
    uint64_t ts = 0;
    for (int i = 0; i < 5; i++) {
        ts |= ((uint64_t)ts_buf[i]) << (i * 8);
    }
    return ts;
}

void resp_msg_set_ts_40bit(uint8_t *ts_field, const uint64_t *ts) {
    for (int i = 0; i < RESP_MSG_TS_LEN; i++) {
        ts_field[i] = (uint8_t)((*ts >> (i * 8)) & 0xFF);
    }
}

void init_scan_response_msg() {
    uint8_t base_header[] = { 0x41, 0x88, 0, 0xFF, 0xFF, 'S', 'C', 'A', 'N', 0x01 };
    memcpy(tx_scan_resp_msg, base_header, ALL_MSG_COMMON_LEN);
    memcpy(&tx_scan_resp_msg[TAG_MAC_PAYLOAD_OFFSET_IN_TX_BUFFER], TAG_MAC, TAG_MAC_PAYLOAD_LEN);
    tx_scan_resp_msg[ALL_MSG_COMMON_LEN] = 0x00; 
    tx_scan_resp_msg[ALL_MSG_COMMON_LEN + 1] = 0x00;
    tx_scan_resp_msg[MSG_SCAN_RESPONSE_LEN - 2] = 0x00;
    tx_scan_resp_msg[MSG_SCAN_RESPONSE_LEN - 1] = 0x00;
}

// ====================================================================
// 3. MAIN LOOP (DELAYED REPLY FIX)
// ====================================================================

void tag_run_rx_loop() {
    uint32_t status_reg = 0;
    dwt_setrxtimeout(RX_TIMEOUT_UUS); 
    dwt_rxenable(DWT_START_RX_IMMEDIATE);
    
    while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR))) {
        yield();
    };

    if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
        uint32_t len = dwt_read32bitreg(RX_FINFO_ID) & RXFLEN_MASK;
        if (len > 30) len = 30; 
        dwt_readrxdata(rx_buffer, len, 0);

        // 1. RANGING POLL
        if (len == POLL_MSG_LEN && rx_buffer[0] == 0x41 && rx_buffer[9] == 0xE0 && rx_buffer[POLL_MSG_ACTION_IDX] == 0x01) 
        {
            if (memcmp(&rx_buffer[POLL_MSG_TAG_MAC_IDX], TAG_MAC, 6) == 0) 
            {
                // Serial.println(F("[MEASURE] Poll RX -> Calculating Delayed Reply..."));

                // A. Get RX Timestamp (40-bit)
                uint8_t poll_rx_ts_buf[5];
                dwt_readrxtimestamp(poll_rx_ts_buf);
                uint64_t poll_rx_ts = get_timestamp_u64(poll_rx_ts_buf);
                
                // B. Compute Scheduled TX Time (RX + Fixed Delay)
                // We calculate the exact time in the future we want the radio to fire.
                uint32_t resp_tx_time = (poll_rx_ts + (POLL_RX_TO_RESP_TX_DLY_UUS * UUS_TO_DWT_TIME)) >> 8;
                dwt_setdelayedtrxtime(resp_tx_time);

                // C. Calculate the Actual TX Timestamp (40-bit)
                // The low 9 bits are zeroed by the hardware in delayed mode + Antenna Delay
                uint64_t resp_tx_ts = (((uint64_t)(resp_tx_time & 0xFFFFFFFEUL)) << 8) + TX_ANT_DLY;
                
                tx_resp_msg[ALL_MSG_SN_IDX] = rx_buffer[ALL_MSG_SN_IDX];
                resp_msg_set_ts_40bit(&tx_resp_msg[RESP_MSG_POLL_RX_TS_IDX], &poll_rx_ts);
                resp_msg_set_ts_40bit(&tx_resp_msg[RESP_MSG_RESP_TX_TS_IDX], &resp_tx_ts); 
                memcpy(&tx_resp_msg[RESP_MSG_ANCHOR_MAC_IDX], &rx_buffer[POLL_MSG_ANCHOR_MAC_IDX], 6);

                // D. Send DELAYED
                dwt_writetxdata(RESP_MSG_LEN, tx_resp_msg, 0);
                dwt_writetxfctrl(RESP_MSG_LEN, 0, 1); 
                
                int ret = dwt_starttx(DWT_START_TX_DELAYED);

                // Check if we missed the window (processing took too long)
                if (ret == DWT_SUCCESS) {
                    while (!(dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK)) { yield(); }
                    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
                    // Serial.println(F("[MEASURE] >> Sent."));
                } else {
                    Serial.println(F("[MEASURE] !! Late TX Error. Increase Delay."));
                    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_TX | SYS_STATUS_ALL_RX_ERR);
                }
            }
        }
        // 2. SCAN POLL (Keep Immediate for discovery as timing is not critical)
        else if (len == SCAN_MSG_LEN && rx_buffer[3] == 0xFF && rx_buffer[4] == 0xFF && rx_buffer[9] == 0x00) {
            Serial.println(F("[SCAN] Discovery RX -> Replying..."));
            init_scan_response_msg();
            tx_scan_resp_msg[ALL_MSG_SN_IDX] = rx_buffer[ALL_MSG_SN_IDX];
            dwt_writetxdata(MSG_SCAN_RESPONSE_LEN, tx_scan_resp_msg, 0);
            dwt_writetxfctrl(MSG_SCAN_RESPONSE_LEN, 0, 1); 
            dwt_starttx(DWT_START_TX_IMMEDIATE);
            
            while (!(dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK)) { yield(); }
            dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
            Serial.println(F("[SCAN] >> Sent."));
        }
    } else {
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);
    }
}

void setup() {
    Serial.begin(115200);
    WiFi.mode(WIFI_STA);
    readMacAddress();
    TAG_ID_STRING = mac_to_string(TAG_MAC);
    Serial.print("Tag: "); Serial.println(TAG_ID_STRING);
    
    _fastSPI = SPISettings(8000000L, MSBFIRST, SPI_MODE0);
    spiBegin(PIN_IRQ, PIN_RST);
    spiSelect(PIN_SS);
    delay(2);
    
    while (!dwt_checkidlerc()) { dwt_softreset(); delay(10); }
    if (dwt_initialise(DWT_DW_INIT) == DWT_ERROR) { UART_puts("INIT FAILED\r\n"); while(1); }

    dwt_setleds(DWT_LEDS_ENABLE | DWT_LEDS_INIT_BLINK);
    if (dwt_configure(&config)) { UART_puts("CONFIG FAILED\r\n"); while(1); }
    
    dwt_configuretxrf(&txconfig_options);
    dwt_setrxantennadelay(RX_ANT_DLY);
    dwt_settxantennadelay(TX_ANT_DLY);
    
    init_scan_response_msg();
    Serial.println("Setup Complete. Delayed TX Enabled.");
}

void loop() {
    tag_run_rx_loop();
    delay(1);
}