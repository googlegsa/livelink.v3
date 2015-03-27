# Introduction #

The LAPI methods for retrieving category and attribute information require many API calls. We'd like to be able to retrieve this information more efficiently by using direct database queries.


# The Way I'm Doing It #

There are a number of tables which seem to be used to store
category and attribute information in Livelink. The method I'm currently using is to read the attribute values
from LLAttrData and the attribute names from CatRegionMap. Here's
a list of the fields which seem to be useful. There are some
others I don't understand the use of.

| **LLAttrData** | | one row per attribute value |
|:---------------|:|:----------------------------|
|  | ID | object id of document, etc. which has a category |
|  | DefID | category object id |
|  | DefVerN | category version in use |
|  | AttrId | integer identifying a particular attribute |
|  | AttrType | LAPI\_ATTRIBUTES.ATTR\_TYPE\_XXX constant |
|  | EntryNum | used for multi-valued attributes |
|  | ValInt | contains integer or boolean value |
|  | ValReal | contains real value |
|  | ValDate | contains date value |
|  | ValStr | contains string value|
|  | ValLong | contains multi-line string value |


| CatRegionMap | | one row per category attribute |
|:-------------|:|:-------------------------------|
|  | CatID | category object id |
|  | CatName | category name |
|  | SetName | set name, if the attribute is a set |
|  | AttrName | attribute display name |
|  | RegionName | Attr

&lt;CatID&gt;



&lt;AttrID&gt;

 |

To find the attribute values for an object, I'm basically using
the following queries, the first to get the values and the second
to get the attribute names for the categories assigned to the
object whose data we're looking up.

select ID, DefID, AttrId, AttrType, EntryNum, ValInt, ValReal,
ValDate, ValStr, ValLong from LLAttrData where ID=

&lt;objId&gt;


order by DefId,AttrId,EntryNum

select CatId,AttrName,RegionName from CatRegionMap
where CatId in (select distinct DefId from LLAttrData where ID=

&lt;catId&gt;

)

After that, I use the attribute type to decide which LLAttrData
valXXX field to read the value from.


# The Way The Api Seems To Do It #

The API seems to read its data from LLAttrBlobData.
LLAttrBlobData seems to be a map from catgorized object id to a
stringified OScript object containing the categories and
attribute data. Here's an example of the data in an
LLAttrBlobData.SegmentBlob field:

```
A<1,?,{694702,1}=A<1,?,'CustomID'=0,'ID'=1,'Values'={A<1,?,2=A<1,?,'
ID'=2,'Values'={A<1,?,3=A<1,?,'ID'=3,'Values'={?}>,4=A<1,?,'ID'=4,'Values'={?}>>
}>,5=A<1,?,'ID'=5,'Values'={A<1,?,6=A<1,?,'ID'=6,'Values'={D/2003/9/19:0:0:0}>,7
=A<1,?,'ID'=7,'Values'={2524}>>}>>}>,{694879,1}=A<1,?,'CustomID'=0,'ID'=1,'Value
s'={A<1,?,2=A<1,?,'ID'=2,'Values'={'first row','second row','third row'}>>}>,{69
4881,1}=A<1,?,'CustomID'=0,'ID'=1,'Values'={A<1,?,2=A<1,?,'ID'=2,'Values'={D/200
2/12/11:0:0:0}>,3=A<1,?,'ID'=3,'Values'={D/2003/1/1:0:0:0}>,4=A<1,?,'ID'=4,'Valu
es'={false}>,5=A<1,?,'ID'=5,'Values'={11}>,6=A<1,?,'ID'=6,'Values'={2}>,7=A<1,?,
'ID'=7,'Values'={'document attributes'}>,8=A<1,?,'ID'=8,'Values'={'first line\r\
nsecond line\r\nthird line'}>,9=A<1,?,'ID'=9,'Values'={'two'}>,10=A<1,?,'ID'=10,
'Values'={2524}>,11=A<1,?,'ID'=11,'Values'={'2002-12-11 00:00:00'}>,12=A<1,?,'ID
'=12,'Values'={'true'}>,14=A<1,?,'ID'=14,'Values'={'2'}>>}>>
```

The API methods ListObjectCategoryIDs and GetObjectAttributesEx
are based on LLIAPI.AttrData, which reads the data from
LLAttrBlobData. It also reads the LLAttrData records, but I can't
see that it ever uses them. It uses the category node ids in the
LLAttrBlobData SegmentBlob field to get the version LLNode and
get the category definition.

If you follow a trail through version data tables starting with
the category object id:

DVersData.DocID = <cat id>
DVersData.ProviderID = 

&lt;id1&gt;


ProviderData.providerID = 

&lt;id1&gt;


ProviderData.providerData = 

&lt;id2&gt;


BlobData.longID = 

&lt;id2&gt;


BlobData.segment = <category version definition>

Here's an example BlobData.segment:
```
A<1,?,'Children'={A<1,?,'Children'={A<1,?,'DisplayLen'=32,'DisplayName'='Text
Field Attribute in
Set','FixedRows'=true,'ID'=3,'Length'=32,'MaxRows'=1,'NumRows'=1,'Required'=false,'Search'=true,'Type'=-1>,A<1,?,'DisplayName'='Integer
Popup Attribute in
Set','FixedRows'=true,'ID'=4,'MaxRows'=1,'NumRows'=1,'Required'=false,'Search'=true,'Type'=12,'ValidValues'={1,2,3}>},'DisplayName'='Attribute
Set
1','FixedRows'=false,'ID'=2,'MaxRows'=2,'NumRows'=1,'Type'=-18>,A<1,?,'Children'={A<1,?,'DisplayName'='Date
Field Attribute in
Set','FixedRows'=true,'ID'=6,'MaxRows'=1,'NumRows'=1,'Required'=false,'Search'=true,'TimeField'=false,'Type'=-7>,A<1,?,'DisplayName'='User
Field Attribute in
Set','FixedRows'=true,'ID'=7,'MaxRows'=1,'NumRows'=1,'Required'=false,'Search'=true,'SelectGroup'=false,'Type'=14>},'DisplayName'='Attribute
Set
2','FixedRows'=true,'ID'=5,'MaxRows'=1,'NumRows'=1,'Type'=-18>},'DisplayName'='Attribute
Set
Category','FixedRows'=true,'ID'=1,'MaxRows'=1,'Name'='Attribute_Set_Category','NextID'=8,'NumRows'=1,'Required'=false,'Type'=-18,'ValueTemplate'=A<1,?,'ID'=1,'Values'={A<1,?,2=A<1,?,'ID'=2,'Values'={A<1,?,3=A<1,?,'ID'=3,'Values'={?}>,4=A<1,?,'ID'=4,'Values'={?}>>}>,5=A<1,?,'ID'=5,'Values'={A<1,?,6=A<1,?,'ID'=6,'Values'={?}>,7=A<1,?,'ID'=7,'Values'={?}>>}>>}>>
```

My guess is that the point in the API where the category object
llnode is retrieved, and NodeFetchVersion is called, is where
this trail is followed and the list of attributes for the
category is retrieved.

After calling GetObjectAttributesEx, we use AttrListNames,
AttrGetInfo,  and AttrGetValues. I can't find references to any
of these methods in Builder, which leads me to believe that it's
possible that they're implemented in the LAPI library only, and
the CatVersion object contains the complete set of attribute
data. The LAPI stuff never seems to use CatRegionMap, for
example, but the info must be somewhere.

It seems possible that using the API methods isn't as expensive
as it seems if all the data is returned by GetObjectAttributesEx,
which is called once for each category assigned to the object.