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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Date as SQLDate
import java.sql.Time as SQLTime
import java.sql.Timestamp as SQLTimestamp
import java.time.ZonedDateTime
import java.util.Date as UtilDate
import java.util.concurrent.ConcurrentHashMap

import org.apache.sling.settings.SlingSettingsService
import org.apache.sling.spi.resource.provider.ResolveContext
import org.json.JSONArray
import org.json.JSONObject

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@spock.lang.Subject(PersistenceHelper)
class PersistenceHelperSpec extends Specification {

    @Shared
    JSONObject properties

    @Shared
    String resourcePath

    @Shared
    SlingSettingsService slingSettings

    @Shared
    FileSystemProviderResource resource

    def setupSpec() {
        InputStream stream = getClass().getResourceAsStream('_sling_fsp_properties.json')
        String jsonStr = stream.getText(StandardCharsets.UTF_8.name())
        properties = new JSONObject(jsonStr)
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
        FileSystemProviderState state = new FileSystemProviderState()
        ResolveContext<FileSystemProviderState> resolveContext = Mock(ResolveContext)
        resolveContext.providerState >> state
        resource = new FileSystemProviderResource(null, null, resolveContext, null, Paths.get('.', resourcePath), resourcePath)
    }

    def cleanupSpec() {
        Util.destroy()
    }


    @Unroll
    def 'test non null conversion for #key in properties1.json'() {
        when:
        JSONObject prop = properties.optJSONObject(key)

        then:
        prop != null

        when:
        Object obj = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

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
        key << ['intValue', 'dblValues', 'floatValues', 'calendarValue', 'zonedDateTimes', 'externalHashMap']
    }


    def 'test read double array'() {
        when:
        JSONObject prop = properties.getJSONObject('dblValues')
        Object obj = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        obj != null
        obj instanceof Double[]
        Double[] arr = obj
        arr[0] == 3.14
        arr[1] == 1.73
    }

    def 'test read float values'() {
        when:
        JSONObject prop = new JSONObject([(FSPConstants.JSON_KEY_TYPE): float.name,
            (FSPConstants.JSON_KEY_VALUES): new JSONArray([5.5f, 7.7f, 8.8f])])
        Object obj = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        obj != null
        obj instanceof float[]
        float[] vals = obj
        vals.length == 3
        vals[0] == 5.5f
        vals[1] == 7.7f
        vals[2] == 8.8f
    }

    def 'test read properties'() {
        when:
        Map<String, Object> props = PersistenceHelper.getProperties(resource)
        Calendar expectedCal = ValueConversion.convert('2016-06-07T19:40:20.618+09:00', Calendar)
        ZonedDateTime expectedZDT1 = ValueConversion.convert('2016-06-07T19:40:20.618+09:00', ZonedDateTime)
        ZonedDateTime expectedZDT2 = ValueConversion.convert('2016-06-07T19:40:20.618+09:00', ZonedDateTime)

        then:
        props != null
        props.size() == 7
        props['sling:resourceType'] == 'my/app/resourceType'
        props['intValue'] == 5L
        props['dblValues'] == [3.14, 1.73] as Double[]
        props['floatValues'] == [1.414, 2.718] as float[]
        props['calendarValue'] == expectedCal
        props['zonedDateTimes'] == [expectedZDT1, expectedZDT2] as ZonedDateTime[]
        props['externalHashMap'] == ['1':1, '2':4, '3':9]
    }

    def 'test read properties file'() {
        when:
        Path propFile = PersistenceHelper.getPropertyFile(resource)
        Path expectedFile = Paths.get('.', resourcePath, "${FSPConstants.FILENAME_PREFIX_FSP}${FSPConstants.FILENAME_FRAGMENT_PROPERTIES_FILE}${JSONCompression.NONE.extension}")

        then:
        propFile
        Files.isSameFile(propFile, expectedFile)
    }

    def 'test read long value'() {
        when:
        JSONObject prop = properties.getJSONObject('intValue')
        Object obj = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        obj != null
        obj instanceof Long
        obj == 5l
    }

    def 'test read serialized concurrent hash map'() {
        when:
        JSONObject prop = properties.getJSONObject('externalHashMap')
        Object obj = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        obj != null
        obj instanceof ConcurrentHashMap
        ConcurrentHashMap map = obj
        map.size() == 3
        map['1'] == 1
        map['2'] == 4
        map['3'] == 9
    }


    def 'test write filebinary'() {
        when:
        FileBinary val = new FileBinary(PersistenceHelper.getPropertyFile(resource))
        JSONObject prop = PersistenceHelper.createJSONPropertyObject(val)

        then:
        prop.getString(FSPConstants.JSON_KEY_TYPE) == FileBinary.class.name
        prop.getBoolean(FSPConstants.JSON_KEY_BINARY)
        String jsonVal = prop.getString(FSPConstants.JSON_KEY_VALUE)
        jsonVal == val.name

        when:
        Object backConvert = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        backConvert instanceof FileBinary
        val == backConvert
    }



    def 'test write int array'() {
        when:
        int[] val = [6000, 88165, 13540350] as int[]
        JSONObject prop = PersistenceHelper.createJSONPropertyObject(val)

        then:
        prop.getString(FSPConstants.JSON_KEY_TYPE) == int.name
        JSONArray jsonArr = prop.getJSONArray(FSPConstants.JSON_KEY_VALUES)
        jsonArr.length() == 3
        jsonArr.getInt(0) == val[0]
        jsonArr.getInt(1) == val[1]
        jsonArr.getInt(2) == val[2]

        when:
        Object backConvert = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        backConvert instanceof int[]
        val == backConvert
    }

    def 'test write null'() {
        when:
        Calendar val = null
        JSONObject prop = PersistenceHelper.createJSONPropertyObject(val)

        then:
        prop.getString(FSPConstants.JSON_KEY_TYPE) == Object.name
        Object jsonVal = prop.get(FSPConstants.JSON_KEY_VALUE)
        JSONObject.NULL.equals(jsonVal)

        when:
        Object backConvert = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        backConvert == null
    }

    def 'test write short'() {
        when:
        Short val = 900 as Short
        JSONObject prop = PersistenceHelper.createJSONPropertyObject(val)

        then:
        prop.getString(FSPConstants.JSON_KEY_TYPE) == Short.name
        int jsonVal = prop.getInt(FSPConstants.JSON_KEY_VALUE)
        jsonVal == val

        when:
        Object backConvert = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        backConvert instanceof Short
        val == backConvert
    }

    def 'test read short array'() {
        when:
        JSONObject prop = new JSONObject([(FSPConstants.JSON_KEY_TYPE): Short.name,
            (FSPConstants.JSON_KEY_VALUES): new JSONArray([5, 7, 8])])
        Object obj = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        obj != null
        obj instanceof Short[]
        Short[] arr = obj
        arr[0] == 5
        arr[1] == 7
        arr[2] == 8
    }

    def 'test write string'() {
        when:
        String val = 'this is my string, I am very proud of it'
        JSONObject prop = PersistenceHelper.createJSONPropertyObject(val)

        then:
        prop.getString(FSPConstants.JSON_KEY_TYPE) == String.name
        String jsonVal = prop.getString(FSPConstants.JSON_KEY_VALUE)
        jsonVal == val

        when:
        Object backConvert = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        backConvert instanceof String
        val == backConvert
    }

    def 'test write date'() {
        when:
        UtilDate val = new UtilDate()
        JSONObject prop = PersistenceHelper.createJSONPropertyObject(val)

        then:
        prop.getString(FSPConstants.JSON_KEY_TYPE) == UtilDate.name
        long jsonVal = prop.getLong(FSPConstants.JSON_KEY_VALUE)
        jsonVal == val.time

        when:
        Object backConvert = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        backConvert instanceof UtilDate
        val == backConvert
    }

    def 'test write sql date types'() {
        when:
//        Supplier<? extends UtilDate> supplier = supplierClosure as Supplier
//        UtilDate val = supplier.get()
        UtilDate val = supplierClosure()

        JSONObject prop = PersistenceHelper.createJSONPropertyObject(val)

        then:
        prop.getString(FSPConstants.JSON_KEY_TYPE) == val.class.name
        String jsonVal = prop.getString(FSPConstants.JSON_KEY_VALUE)
        jsonVal == val.toString()

        when:
        Object backConvert = PersistenceHelper.readJSONPropertyValue(resourcePath, prop)

        then:
        val.class.isInstance(backConvert)
        val == backConvert

        where:
        supplierClosure | _
        // need to normalize the date and time per the clauses indicated in their APIs. easiest to do this by toString and back
        { -> SQLDate.valueOf(new SQLDate(System.currentTimeMillis()).toString()) } | _
        { -> SQLTime.valueOf(new SQLTime(System.currentTimeMillis()).toString()) } | _
        { -> SQLTimestamp t = new SQLTimestamp(System.currentTimeMillis()); t.setNanos(5000000); t } | _
    }
}
