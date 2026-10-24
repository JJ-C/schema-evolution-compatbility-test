package com.waltest.protoevolution.jackson;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The "do it properly" version of a typed v1 DTO: explicit
 * getters/setters for the fields v1 actually knows about, PLUS
 * {@code @JsonAnySetter}/{@code @JsonAnyGetter} to catch and re-emit
 * anything it doesn't.
 *
 * Unlike {@link ScenarioReader.PersonV1} (used in --typed mode), this
 * class:
 *   - never throws on an unrecognized property during deserialize
 *     (no need to disable FAIL_ON_UNKNOWN_PROPERTIES -- the any-setter
 *     IS the "I'll take whatever you've got" handler)
 *   - never silently drops anything on serialize -- unknown fields
 *     are parked in {@link #overflow} and re-emitted alongside the
 *     known ones.
 *
 * Nobody adds this by default. It's extra boilerplate for a problem
 * most people don't know they have until it bites them. That's the
 * actual lesson: the "safe" typed option exists, it's just friction
 * most teams never pay unless forward-compatibility is an explicit
 * requirement from day one.
 */
public class PersonV1Safe {

    private String name;
    private int age;
    private final Map<String, Object> overflow = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @JsonAnySetter
    public void setOverflow(String key, Object value) {
        overflow.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getOverflow() {
        return overflow;
    }
}
