CSV: UTF-8 with BOM. Empty fields mean missing values.
HR/RR received_unix_ms: phone notification reception time, not individual beat time.
ECG/ACC device_timestamp_ns: original device clock, not Unix time.
RR beat_index counts available intervals; missing beats are not reconstructed.
All stored samples are exported without filtering or chart downsampling.
