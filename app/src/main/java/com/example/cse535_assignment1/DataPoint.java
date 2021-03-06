package com.example.cse535_assignment1;

import java.util.Date;

class DataPoint<T> {
    final Date timestamp;
    final T measurement;

    DataPoint(Date timestamp, T measurement) {
        this.timestamp = timestamp;
        this.measurement = measurement;
    }
}
