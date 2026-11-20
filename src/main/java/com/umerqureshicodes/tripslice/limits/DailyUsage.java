package com.umerqureshicodes.tripslice.limits;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

// How many of one kind of paid action (TwelveLabs analyze, search, montage) a user has used on one UTC day
@Entity
@Table(name = "daily_usage")
public class DailyUsage {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "user_id")
        private Long userId;
        @Column(name = "day")
        private LocalDate day;
        @Column(name = "kind", length = 16)
        private String kind;

        protected Key() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(userId, key.userId) && Objects.equals(day, key.day) && Objects.equals(kind, key.kind);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, day, kind);
        }
    }

    @EmbeddedId
    private Key id;

    @Column(name = "count", nullable = false)
    private int count;

    protected DailyUsage() {}
}
