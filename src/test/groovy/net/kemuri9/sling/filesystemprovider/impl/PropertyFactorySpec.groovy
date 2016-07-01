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

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

import org.apache.sling.commons.json.JSONArray
import org.apache.sling.commons.json.JSONObject
import org.apache.sling.settings.SlingSettingsService

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@spock.lang.Subject(PropertyFactory)
class PropertyFactorySpec extends Specification {

    @Shared
    JSONObject props1

    @Shared
    String resourcePath

    @Shared
    SlingSettingsService slingSettings

    def setupSpec() {
        InputStream stream = getClass().getResourceAsStream('properties1.json')
        String jsonStr = new InputStreamReader(stream, StandardCharsets.UTF_8).getText()
        props1 = new JSONObject(jsonStr)
        // can pretty much assume that the java user dir is the project directory
        resourcePath = "/src/test/resources/${getClass().package.name.replaceAll('\\.', '/')}"

        // Mock out a simple sling settings as it's utilized for file path lookup
        slingSettings = Mock(SlingSettingsService)
        FileSystemProviderConfig config = TestUtil.newConfig()
        slingSettings.slingHomePath >> new File('.').canonicalPath
        slingSettings.getAbsolutePathWithinSlingHome(_ as String) >> { String relPath ->
            new File(relPath).absolutePath
        }

        Util.init(slingSettings, config)
    }

    def 'test float values'() {
        when:
        JSONObject prop = new JSONObject([(FSPConstants.JSON_KEY_TYPE): float.name,
            (FSPConstants.JSON_KEY_VALUES): new JSONArray([5.5f, 7.7f, 8.8f])])
        Object obj = PropertyFactory.createPropertyValue(resourcePath, prop)

        then:
        obj != null
        obj instanceof float[]
        float[] vals = obj
        vals.length == 3
        vals[0] == 5.5f
        vals[1] == 7.7f
        vals[2] == 8.8f
    }

    def 'test long value'() {
        when:
        JSONObject prop = props1.getJSONObject('intValue')
        Object obj = PropertyFactory.createPropertyValue(resourcePath, prop)

        then:
        obj != null
        obj instanceof Long
        obj == 5l

    }

    def 'test double array'() {
        when:
        JSONObject prop = props1.getJSONObject('dblValues')
        Object obj = PropertyFactory.createPropertyValue(resourcePath, prop)

        then:
        obj != null
        obj instanceof Double[]
        Double[] arr = obj
        arr[0] == 3.14
        arr[1] == 1.73
    }

    @Unroll
    def 'test non null conversion for #key in properties1.json'() {
        when:
        JSONObject prop = props1.optJSONObject(key)

        then:
        prop != null

        when:
        Object obj = PropertyFactory.createPropertyValue(resourcePath, prop)

        then:
        obj != null
        if (obj instanceof Object[]) {
            for (Object objI : obj as Object[]) {
                assert objI != null
            }
        }
        String className = ((obj.getClass().array) ? obj.getClass().componentType : obj.getClass()).name
        className == prop.optString(FSPConstants.JSON_KEY_TYPE)

        where:
        key << ['intValue', 'dblValues', 'calendarValue', 'zonedDateTimes', 'externalHashMap']
    }

    def 'test serialized concurrent hash map'() {
        when:
        JSONObject prop = props1.getJSONObject('externalHashMap')
        Object obj = PropertyFactory.createPropertyValue(resourcePath, prop)

        then:
        obj != null
        obj instanceof ConcurrentHashMap
        ConcurrentHashMap map = obj
        !map.empty
        map.size() == 3
        map['1'] == 1
        map['2'] == 4
        map['3'] == 9
    }

    def 'test short array'() {
        when:
        JSONObject prop = new JSONObject([(FSPConstants.JSON_KEY_TYPE): Short.name,
            (FSPConstants.JSON_KEY_VALUES): new JSONArray([5, 7, 8])])
        Object obj = PropertyFactory.createPropertyValue(resourcePath, prop)

        then:
        obj != null
        obj instanceof Short[]
        Short[] arr = obj
        arr[0] == 5
        arr[1] == 7
        arr[2] == 8
    }
}
