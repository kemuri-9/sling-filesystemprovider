# Design Document #

In progress design outline of how the functionality is expected to be implemented.

## Java 8 ##
Java 8 is a requirement for usage, due to using/supporting Java 8 types, such as ``java.util.Base64`` and the ``java.time.*`` types

## All resources are Folders ##
To avoid complexity with needing to possibly handle that resources may be folders or files, it would be simpler to have them always be folders.

As a result of this, The resource tree will directly map onto the filesystem, such that filesystem paths will be at least as long as the resource tree itself.

As such, Windows operating systems are not recommended for use here, due to its much smaller limit (without writing a bunch of C code to access the particular APIs that can bypass this).

So use Linux systems if planning to have decently sized/deep resource trees.

## Resource Properties ##
Resource properties will be stored within a JSON file.
The format will be a single object that has keys of the property names to a JSON object that stores the information about that property name.

It is currently expected that the Sling JSON commons functionality be used as appropriate here.

### Type ###
the ``type`` field of the inner object stores the FQN of the java Class. e.g. ``java.lang.Double``, ``java.lang.Float``

### Value/Values ###
Only one of ``value`` or ``values`` may be defined.
* If ``value`` is utilized, the corresponding value is singly-valued, this also indicates that when retrieving the property, it is singly-valued and is returned in the type it is defined as.
* If ``values`` is utilized, the corresponding value is JSON array of values for the property. It also indicates that the property will be treated as an array type on return.

The value(s) stored will match the java type where JSON supports it (boolean, integer/long, string). otherwise the value will be converted to an appropriate storable format for persistence.
#### Storage conversion ####
* ``java.lang.Number`` - store numeric value as is
* ``java.lang.Boolean`` - store boolean value
* ``java.util.Date`` - store milliseconds numeric value
* ``java.util.Calendar`` - store in ISO 8601 date format
* ``java.time.*`` - store in appropriate string format as compliant with ISO 8601 as possible
* ``java.io.Serializable`` - store serialized binary
* ``java.io.InputStream`` - store binary stream

other types are likely to not be persisted.

#### Binary Storage ####
JSON is rather ill-fit for binary storage, being a text-based format. So in addition to supporting the storage of the binary data as Base64 within the JSON file, it should also be supported to store the values in external files on the file system. A configuration option should control if binaries are stored within the JSON or in files.  

### Special Keys ####
The following are special property names and can not be used for other purposes
* ``sling:resourceType`` - stores the resource's type
* ``sling:resourceSuperType`` - stores the resource's parent type

### Example ###

```json
{
  "sling:resourceType": {
    "type": "java.lang.String",
    "value": "my/app/resourceType"
  },
  "intValue" : {
    "type": "java.lang.Integer",
    "value": 5
  },
  "dblValues" : {
    "type": "java.lang.Double",
    "values": [3.14, 1.73]
  },
  "calendarValue" : {
    "type": "java.util.Calendar",
    "value": "2016-06-07T19:40:20.618+0900"
  },
  "zonedDateTimes" : {
    "type": "java.time.ZonedDateTime",
    "values": ["2016-06-07T19:40:20.618+0900", "2016-06-07T19:40:20.618+0900"]
  },
  "binaryData" : {
    "type": "java.io.InputStream",
    "value": "rO0ABXNyABtqYXZhLnV0aWwuR3JlZ29yaWFuQ2F..."
  },
  "externalPrivateKey" : {
    "type": "java.security.PrivateKey",
    "value": "_sling_fsp_externalPrivateKey_1465296780859",
	"external": true
  }
}
```

### Additional Applicable Configuration ###
#### Compression ####
As JSON files are rather text-y, they also tend to be easily compressible. So a configuration option should be included to allow compressing the JSON file as any reasonable format supported by the native JVM library.

##### Default #####
Do not compress

#### Pretty Print JSON ####
Sometimes you just want to be able to read the data easily, so add a pretty-print configuration option to allow for pretty-printing.

##### Default #####
Do not pretty-print (false)

## Query Languages ##
For the initial implementation, it will be expected that no query languages will be supplied by the provider.
The only natural one that comes to mind as being known well enough and could apply is XPATH as well, so this could be considered for a later time.

## MVCC ##
It is not completely decided if MVCC will or will not be supported at this time.
There is some possible of having it supported by having "history" files associated to resources (one per resource, and likely stored in the resource's folder) that indicate their history of life, evolution, and death that could be utilized for this purpose.

It is not yet known if lacking MVCC will cause major problems elsewhere in the system, as the other major implementations of resource provision do support MVCC

## Version Support ##
It needs further investigation/discussion as to what versions of Sling and technologies that utilize Sling should be supported.

That is how far back in time should be supported, and adjusting dependencies/usages as appropriate.

## Security ##
For the first phase, security will not be particularly implemented.

After the first phase, what will likely be required is to create a security API and implementation that utilizes the resource tree for storing principals and their permissions.
the existing ``ResourceAccessSecurity`` API may be usable here, but it is incomplete in the whole picture, especially around management of principals and permissions.
There needs to be a way to create/manage principals and their permissions through the system itself

### Principals ###
There are two types of Principals, users and groups.
users and groups will share the same account/id pool, so there can not be a user and group with the same account/id.

#### Users ####
* Users are accounts/ids that can actually log into the system.
* Users can be in any number of groups.

#### Groups ####
* Groups are generic accounts/ids that can **not** be logged into.
* Groups can be in any number of groups.
* Attempting to create circular group memberships should be denied to prevent later logic errors.

#### Special Accounts ####
Specific special/standard accounts to the system are the following
* ``administrators`` - group indicating administrator permissions
* ``admin`` - user account indicating a generic admin account, needed for initial setup/creation
* ``everyone`` - group that all users are a member of by default, though the membership **can be removed**
* ``anonymous`` - user account for unauthorized/anonymous access

### Permissions ###
There are two major categories of Permissions
#### Resource Permissions ####
* Allow/Deny Read - see this resource and possibly its children
* Allow/Deny Update - can make changes to the resource (modify its properties or type)
* Allow/Deny Delete - delete the resource
* Allow/Deny Execute - the resource can be executed as a script
* Allow/Deny Creating Children - can create children of the resource
* Allow/Deny Deleting Children - can delete children of the resource

* Allow/Deny Read Access Control - can read ACLs associated to the resource (and possibly children)
* Allow/Deny Update Access Control - can modify ACLs associated to the resource (and possibly children)

#### Property Permissions ####
* Allow/Deny Read
* Allow/Deny Update
* Allow/Deny Delete

#### Aggregate Permissions ####
For simplicity in authoring permissions, there should be some aggregate permission sets, possibly the following.
* Allow/Deny "Property All" (Read Property, Update Property, Delete Property)
* Allow/Deny "Resource Self All" (Read Resource, Delete Resource, Execute Resource)
* Others?

#### Permission Priority ####
* principal permissions before permissions of groups the principal is a member of
* Leaf Group permissions before parent Group permissions (least distance from initial user wins)
* Property Permissions before Resource Properties
* Current Resource Permissions before Parent hierarchy Permissions
* Denies (of same priority) before Allows (of same priority)
* Read (of same priority) before Modify/Delete (of same priority) **evaluate this further**

#### Globbing ####
all types of permissions should support globbing to ensure the usefulness of the permission being set (that is not having to set the same permission at different levels)
However, the globbing should **not** be restricted to a single glob, allowing for multiple globs.

#### Possible Scenarios ####
1) Applying a Deny Resource Update, but then applying an Allow Property Update permission on particular globs.

This should result in the particular properties being modifiable, per the specified glob(s) on Allow Property Update.

2) Applying a Deny Resource Read, but then applying an Allow Property Read on particular globs.

This will inherently require that the resource can be read (discovered) to read those particularly allowed properties.

3) Attempting to Delete a child resource with a Deny "Delete Resource" under a parent with "Deleting Children Resources" as Allowed.

The operation should fail for invalid access. (Deny Resource Delete on child takes precedence)

Any attempt to delete parent resources of the deny "Delete Resource" child will also fail due to invalid access.

4) Group A exists and has a Deny Read Resource on ``/some/path``
Group B exists and has an Allow Read Resource on ``/some/path``
User Z has no permissions applicable, and is a member of both A and B.

Group A's deny rule wins, causing User Z to not be able to read ``/some/path``

5) Group A exists and has an effective Deny Read Resource on ``/some/path``
Group B exists and has an effective Allow Modify Resource on ``/some/path``

Group A's Deny read rule wins, causing User Z to not be able to read ``/some/path`` even for modification purposes.

6) Group A exists
Group B exists and is a member of A
User Z exists and is a direct member of both A and B.

for User Z, Group A's permissions are no longer overridden by Group B, due to the distance from Z to A only being ``1`` (Z -> A) instead of ``2`` (Z -> B -> A)
