#include "mock_zmq.h"

/**
 * Send simulation - reads data and computes checksum
 * This simulates a read-only operation like sending data to a socket
 */
int64_t mock_send(const uint8_t* data, size_t len) {
    int64_t checksum = 0;

    // Read all bytes and compute checksum
    for (size_t i = 0; i < len; i++) {
        checksum += data[i];
    }

    return checksum;
}

/**
 * Receive simulation - writes pattern data to buffer
 * This simulates a write operation like receiving data from a socket
 */
int64_t mock_recv(uint8_t* buf, size_t len) {
    // Write sequential pattern to buffer
    for (size_t i = 0; i < len; i++) {
        buf[i] = (uint8_t)(i & 0xFF);
    }

    return (int64_t)len;
}

/**
 * Transform simulation - in-place XOR operation
 * This simulates a read-write operation like encryption/decryption
 */
void mock_transform(uint8_t* data, size_t len) {
    // XOR each byte with 0xAA pattern
    for (size_t i = 0; i < len; i++) {
        data[i] ^= 0xAA;
    }
}
