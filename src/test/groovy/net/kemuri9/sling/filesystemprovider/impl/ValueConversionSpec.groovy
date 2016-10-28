/**
 * Copyright 2016 Steven Walters
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kemuri9.sling.filesystemprovider.impl

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import spock.lang.Shared
import spock.lang.Specification

@spock.lang.Subject(ValueConversion)
class ValueConversionSpec extends Specification {

    @Shared
    DateTimeFormatter JT_ISO8601

    def setupSpec() {
        JT_ISO8601 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")
    }

    def 'test conversion of long to other numbers'() {
        setup:
        long lval = 300l

        when:
        Object obj = ValueConversion.convert(lval, targetClass)

        then:
        obj != null
        obj instanceof Number
        Number num = obj
        num.longValue() == lval

        when:
        Object backToLong = ValueConversion.convert(num, Long)

        then:
        backToLong instanceof Long
        Long longVal = backToLong
        longVal == lval

        where:
        targetClass << [Long, Short, Float, Double, Integer, BigDecimal, BigInteger] // no Byte due to the truncation
    }

    def 'test conversion of short to BigInteger'() {
        setup:
        short sval = 400

        when:
        Object obj = ValueConversion.convert(sval, BigInteger)

        then:
        obj != null
        obj instanceof BigInteger
        BigInteger big = obj as BigInteger
        big.shortValue() == sval
    }

    def 'test conversion of primitive boxing'() {
        setup:
        Short[] vals = [5 as Short, 8 as Short, 400 as Short, 600 as Short] as Short[]

        when:
        Object primArr = ValueConversion.convert(vals, short[].class)

        then:
        primArr != null
        primArr instanceof short[]
        short[] primVals = primArr
        primVals == vals
    }

    def 'test conversion from collection'() {
        setup:
        ArrayList<Long> vals = [5l, 40l, 9000000l, 200000000000l]

        when:
        Object arrVals = ValueConversion.convert(vals, long[].class)

        then:
        arrVals != null
        arrVals instanceof long[]
        long[] primVals = arrVals
        primVals == vals
    }

    def 'test conversion from iterator'() {
        setup:
        ArrayList<Double> vals = [690.0, 500.0, 300.0, 200.0]

        when:
        Object arrVals = ValueConversion.convert(vals.iterator(), float[].class)

        then:
        arrVals != null
        arrVals instanceof float[]
        float[] primVals = arrVals
        primVals == vals
    }

    def 'test convert from single value to single entry array'() {
        setup:
        Integer i = 5

        when:
        Object arrVals = ValueConversion.convert(i, byte[].class)

        then:
        arrVals != null
        arrVals instanceof byte[]
        byte[] primVals = arrVals
        primVals.length == 1
        primVals[0] == i
    }

    def 'test conversion of Calendar'() {
        setup:
        String time = '2016-07-02T03:53:11.335+09:00'
        /* 2016-07-02 - groovy doesn't currently support the java 8 way of turning a function
         * into an interface, so still need to use groovy's own way through use of a closure */
        ZonedDateTime zdt = JT_ISO8601.parse(time, ZonedDateTime.&from)
        Calendar cal = GregorianCalendar.from(zdt)

        when:
        Object str1 = ValueConversion.convert(cal, String.class)

        then:
        str1 != null
        str1 instanceof String
        str1 == time

        when:
        Object cal2 = ValueConversion.convert(str1, Calendar.class)

        then:
        cal2 != null
        cal2 instanceof Calendar
        cal2 == cal

        when:
        Object date1 = ValueConversion.convert(cal2, Date.class)

        then:
        date1 != null
        date1 instanceof Date
        cal.time == date1
        cal2.time == date1

        when:
        Object str2 = ValueConversion.convert(date1, String.class)

        then:
        str2 != null
        str2 instanceof String
        str2 == time

        when:
        Object cal3 = ValueConversion.convert(zdt, Calendar.class)

        then:
        cal3 != null
        cal3 instanceof Calendar
        cal3 == cal
    }
}
