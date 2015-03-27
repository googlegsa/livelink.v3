<h1>Configure the connectorInstance.xml file</h1>

This page describes the advanced configuration properties in connectorInstance.xml.



## Prepare to edit connectorInstance.xml ##

The connector instance and the connectorInstance.xml file are interdependent. You cannot edit the connectorInstance.xml file until you create a connector instance, but you do not want to index content until you have edited the file. If you have already created a connector instance and need to edit the advanced configuration properties, then you may need to reset the connector traversal.

To create a new connector instance, edit connectorInstance.xml, and then start indexing content:

  1. In the GSA Admin Console, go to the Connectors page and add a connector instance.
  1. On the Add Connector page, you need to disable the traversal. There are two ways of doing this:
    * In GSA 6.2, you can select the **Disable traversal** check box.
    * In earlier versions, you can select a schedule from 1:00 AM to 1:00 AM.
  1. Click the **Save Configuration** button.
  1. Edit the connectorInstance.xml file in a text editor. The file is located in your Google connectors installation tree under Tomcat/webapps/connector-manager/connectors/Livelink\_Enterprise\_Server/_connector-name_.
  1. Edit the connector in the Admin Console, and enable the traversal:
    * In GSA 6.2, you can deselect the **Disable traversal** check box.
    * In earlier versions, you can select a schedule from 12:00 AM to 12:00 AM, or any non-empty interval.
  1. Click the **Save Configuration** button.

To edit connectorInstance.xml for an existing connector instance, and then reset the traversal:

  1. Edit the connectorInstance.xml file in a text editor. The file is located in your Google connectors installation tree under Tomcat/webapps/connector-manager/connectors/Livelink\_Enterprise\_Server/_connector-name_.
  1. In the GSA Admin Console, go to the Connectors page and click the Edit link for the connector instance.
  1. Click the **Save Configuration** button. You do not need to make any changes on the page.
  1. Click the Reset link for the connector instance, and then click OK in the popup dialog.

## Default connectorInstance.xml file ##

The default configuration file looks like this:

```
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN//EN" "http://www.springframework.org/dtd/spring-beans-2.0.dtd">
<beans>
    <!-- EDIT THIS BEAN DEFINTION WITH CUSTOM PROPERTY VALUES -->
    <bean id="Livelink_Enterprise_Server"
          class="com.google.enterprise.connector.otex.LivelinkConnector"
          parent="Livelink_Enterprise_Server_Defaults" 
          dependency-check="all" scope="prototype">

        <!-- SET CUSTOM PROPERTY VALUES HERE -->

    </bean>
</beans>
```

## Editing the file ##

The default values of the properties can be modified by inserting
the modified value into the file below the following line:
```
        <!-- SET CUSTOM PROPERTY VALUES HERE -->
```

For example, to set the windowsDomain property to "example", you
would add the following XML `property` element to the file:
```
        <!-- SET CUSTOM PROPERTY VALUES HERE -->
        <property name="windowsDomain" value="example"/>
```

The changes will take effect when you restart Tomcat or edit and
save the connector configuration in the GSA Admin Console.

### Map and list properties ###

In the cases where the property values are maps or lists, you can copy
the default values and add or remove entries. If you only want to add
new entries, it is simpler to use `merge="true"`. For example, to add
an entry to use Livelink /open URLs for documents, you can use the
following:

```
<property name="displayPatterns">
    <map merge="true">
        <entry key="144" value="/open/{0}" />
    </map>
</property>
```

## Configuration properties ##

The following table describes the configuration properties, the
equivalent Livelink properties, the possible values, and the
default value.

### Connection Properties ###

#### connection ####
> The database connection to use.

> <em>Livelink API:</em> <tt>connection</tt> parameter.

> If the value is empty the default database connection, as
> specified in the opentext.ini file, will be used.

> <em>Default value:</em>
```
    <property name="connection" value=""/>
```

#### domainAndName ####
> This property is based on the Livelink security parameter of the same
> name ("<tt>DomainAndName</tt>"), which has the boolean values
> <tt>FALSE</tt> and <tt>TRUE</tt>. The
> values are extended for the connector environment.

> <dl>
<blockquote><dt><tt>FALSE</tt>
<dd>Do no use the identity domain or the DNS-style domain.</dd>
<dt><tt>LEGACY</tt>
<dd>Do not use the identity domain, but allow DNS-style domains in<br>
the username for authentication. This matches the previous behavior and is<br>
the default value.</dd>
<dt><tt>AUTHENTICATION</tt>
<dd>Use the identity domain only for authentication.</dd>
<dt>TRUE</dt>
<dd>Use the identity domain for authentication and authorization.</dd></blockquote>

<blockquote>For authentication, a DNS-style domain name in the username is preserved.<br>
For example: <tt>johndoe@example.com</tt>. The configured<br>
<tt>windowsDomain</tt> will also be used for authentication if no<br>
domain appears in the identity or if such domains are disabled by setting<br>
this property to <tt>FALSE</tt> or <tt>LEGACY</tt>.</blockquote>

<blockquote>For authorization, the DNS-style domains are not preserved and the<br>
<tt>windowsDomain</tt> property is never used.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="domainAndName" value="legacy"/&gt;<br>
</code></pre></blockquote>

<blockquote><em>Since:</em> 2.8</blockquote>

<h4>windowsDomain</h4>
<blockquote>The Windows domain to authenticate users against.</blockquote>

<blockquote><em>Livelink API:</em> <tt>userName</tt> parameter.</blockquote>

<blockquote>The Windows domain is prepended to the username of search users<br>
for authentication. This value is not used for the Livelink<br>
system administrator or the traversal user. This parameter will<br>
always be used, whether authentication is done via HTTP<br>
tunneling or not.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="windowsDomain" value=""/&gt;<br>
</code></pre></blockquote>

<h3>HTTP Tunneling Properties</h3>

<h4>verifyServer</h4>
<blockquote>Specifies whether to verify the Web server's certificate when<br>
the Use HTTPS property is set to true.</blockquote>

<blockquote><em>Livelink API:</em> <tt>VerifyServer</tt> attribute in <tt>config</tt> parameter.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="verifyServer" value="false"/&gt;<br>
</code></pre></blockquote>

<h4>caRootCerts</h4>
<blockquote>The root certificates to use when verifying the Web server's<br>
certificate.</blockquote>

<blockquote><em>Livelink API:</em> <tt>CARootCerts</tt> attribute in <tt>config</tt> parameter.</blockquote>

<blockquote>This property is used only if HTTP tunneling is enabled<br>
(through the livelinkCgi property), and the https and<br>
verifyServer properties are both set to true. Each value in the<br>
list may be either a Base64 encoded X.509 format certificate or a<br>
path to a directory on the Connector Manager host which contains<br>
one or more files containing such certificates.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="caRootCerts"&gt;<br>
        &lt;list&gt;&lt;/list&gt;<br>
    &lt;/property&gt;<br>
</code></pre></blockquote>

<h3>Separate Authentication Properties</h3>

<h4>authenticationConnection</h4>
<blockquote>The database connection to use.</blockquote>

<blockquote><em>Livelink API:</em> <tt>connection</tt> parameter.</blockquote>

<blockquote>If the value is empty the default database connection, as<br>
specified in the opentext.ini file, will be used.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="authenticationConnection" value=""/&gt;<br>
</code></pre></blockquote>

<h4>authenticationVerifyServer</h4>
<blockquote>Specifies whether to verify the Web server's certificate when<br>
the authenticationHttps property is set to true.</blockquote>

<blockquote><em>Livelink API:</em> <tt>VerifyServer</tt> attribute in <tt>config</tt> parameter.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="authenticationVerifyServer" value="false"/&gt;<br>
</code></pre></blockquote>

<h4>authenticationCaRootCerts</h4>
<blockquote>The root certificates to use when verifying the Web server's<br>
certificate.</blockquote>

<blockquote><em>Livelink API:</em> <tt>CARootCerts</tt> attribute in <tt>config</tt> parameter.</blockquote>

<blockquote>This property is used only if HTTP tunneling is enabled<br>
(through the authenticationLivelinkCgi property), and the<br>
authenticationHttps and authenticationVerifyServer properties are<br>
both set to true. Each value in the list may be either a Base64<br>
encoded x.509 format certificate or a path to a directory on the<br>
Connector Manager host which contains one or more files containing<br>
such certificates.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="authenticationCaRootCerts"&gt;<br>
        &lt;list&gt;&lt;/list&gt;<br>
    &lt;/property&gt;<br>
</code></pre></blockquote>

<h3>Indexing Properties</h3>

<h4>startDate</h4>
<blockquote>The earliest modification date for items to be indexed.</blockquote>

<blockquote>The repository is traversed in modification date order,<br>
beginning with the earliest item. When a start date is specified,<br>
the indexing begins with items modified on or after the start<br>
date. Items with an earlier modification date will not be<br>
indexed.</blockquote>

<blockquote>Start dates may be specified as date/time values using the<br>
format "YYYY-mm-dd hh:mm:ss" or as date values using the<br>
format "YYYY-mm-dd". For example, valid startDate values<br>
are "2007-01-01 01:30:00" or "2007-01-01".</blockquote>

<blockquote>If the value is empty or cannot be parsed, all items will be<br>
indexed.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="startDate" value=""/&gt;<br>
</code></pre></blockquote>

<h4>publicContentUsername</h4>
<blockquote>Indexed items that are accessible by this Livelink user are<br>
marked as public documents. Public documents do not require<br>
authentication or authorization.</blockquote>

<blockquote>If the value specified for this property matches the traversal<br>
username (whether explicitly specified, or implicitly, as the<br>
system administrator), then all indexed content will be marked as<br>
public without checking permissions.</blockquote>

<blockquote><b>Note: This property bypasses Livelink<br>
security.</b> Items that are made public are shown in the search<br>
results without authentication or authorization. The content of<br>
these items is available through the "Cached" and "Text Version"<br>
links. These items are not accessible within Livelink without<br>
authentication unless you have a Livelink customization that makes<br>
this possible.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="publicContentUsername" value=""/&gt;<br>
</code></pre></blockquote>

<h4>publicContentDisplayUrl</h4>
<blockquote>Specifies the Livelink URL which should be used in search<br>
results which link to public documents.</blockquote>

<blockquote>If the value is empty, the value of the displayUrl property will<br>
be used.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="publicContentDisplayUrl" value=""/&gt;<br>
</code></pre></blockquote>

<h4>excludedNodeTypes</h4>
<blockquote>Specifies the node types that you want to exclude from<br>
indexing. Excluding a node type means that no items of that type<br>
are indexed.</blockquote>

<blockquote>A comma-separated list of subtype numbers, optionally enclosed<br>
by braces. This value may be copied from the<br>
<tt>ExcludedNodeTypes</tt> parameter in the<br>
<code>[LivelinkExtractor]</code> section of the opentext.ini file.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="excludedNodeTypes"<br>
              value="137,142,143,148,150,154,161,162,201,203,209,210,211,345,346,361,374,431,441,3030004,3030201"/&gt;<br>
</code></pre></blockquote>

<h4>excludedVolumeTypes</h4>
<blockquote>Specifies the volume types that you want to exclude from<br>
indexing. Excluding a volume type means that no volumes of that<br>
type or any of the items that the volumes contain are indexed.</blockquote>

<blockquote>This property is not hierarchical. Only items whose OwnerID<br>
matches a volume of the excluded type will be excluded. Items in<br>
non-excluded subvolumes will be indexed. To exclude entire<br>
hierarchies, specify the items to be excluded by object ID rather<br>
than type, using the excludedLocationNodes property.</blockquote>

<blockquote>By default, the Undelete workspace (subtype 402) is not<br>
excluded. If the Undelete workspace is excluded, either here or by<br>
object ID in excludedLocationNodes, then previously indexed<br>
documents now in the Undelete workspace will be excluded by the<br>
authorization checks.</blockquote>

<blockquote>A comma-separated list of volume type numbers, optionally<br>
enclosed by braces. If this property is not specified, no volume<br>
types are excluded from indexing. This value may be copied<br>
from the <tt>ExcludedVolumeTypes</tt> parameter in the<br>
<code>[LivelinkExtractor]</code> section of the opentext.ini file.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="excludedVolumeTypes" value="148,161,162"/&gt;<br>
</code></pre></blockquote>

<h4>excludedLocationNodes</h4>
<blockquote>Specifies the IDs of the nodes that you want to exclude from<br>
indexing. Excluding a node means that it is not indexed, and<br>
if it is a container, none of the items it contains are<br>
indexed.</blockquote>

<blockquote>Excluding nodes requires the use of the DTreeAncestors table in<br>
Livelink, which in turn requires that the Livelink Recommender<br>
agent be enabled. The latest monthly Livelink patches are<br>
recommended.</blockquote>

<blockquote>A comma-separated list of object IDs, optionally enclosed by<br>
braces.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="excludedLocationNodes" value=""/&gt;<br>
</code></pre></blockquote>

<h4>includedExtendedData</h4>
<blockquote>A map of the ExtendedData attributes that are indexed and used<br>
to construct the HTML content. Each subtype can have different<br>
attributes indexed.</blockquote>

<blockquote>The map contains keys that consist of comma-separated subtype<br>
integers. The special string "default" is not supported. These are<br>
mapped to a comma-separate list of attribute names, which should<br>
appear in the ExtendedData field of the given subtypes.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="includedExtendedData"&gt;<br>
        &lt;map&gt;<br>
            &lt;entry key="130,134" value="Content" /&gt;<br>
            &lt;entry key="202" value="Mission,Goals,Objectives,Initiatives" /&gt;<br>
            &lt;entry key="206" value="Instructions,Comments" /&gt;<br>
            &lt;entry key="208" value="Headline,Story" /&gt;<br>
            &lt;entry key="218" value="Instruction,Questions" /&gt;<br>
        &lt;/map&gt;<br>
    &lt;/property&gt;<br>
</code></pre></blockquote>

<h4>includedObjectInfo</h4>
<blockquote>The object info attributes that you want to index.</blockquote>

<blockquote>A comma-separated list of attribute names. For a list of<br>
object info attributes, see "ObjectInfo Attributes" in the<br>
<em>Livelink API Developer's Reference Guide</em> from Open Text.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="includedObjectInfo" value="" /&gt;<br>
</code></pre></blockquote>

<h4>includedVersionInfo</h4>
<blockquote>The version info attributes that you want to index.</blockquote>

<blockquote>A comma-separated list of attribute names. For a list of<br>
version info attributes, see "VersionInfo Attributes" in the<br>
<em>Livelink API Developer's Reference Guide</em> from Open Text.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="includedVersionInfo" value="" /&gt;<br>
</code></pre></blockquote>

<h4>includedCategories</h4>
<blockquote>The IDs of the categories that you want to index. Including a<br>
category means that when that category is applied to an item, the<br>
attributes are indexed as metadata for that item.</blockquote>

<blockquote>A comma-separated list of object IDs for categories, and/or one of<br>
the following special strings:<br>
<dl>
<dt><tt>all</tt>
<dd>All categories that are applied to the item are indexed.</dd>
<dt><tt>searchable</tt>
<dd>Only attributes that have the <b>Show in Search</b> parameter<br>
enabled, from each of the categories that are applied to the item,<br>
are indexed.  Otherwise, all attributes of the categories are indexed.</dd>
<dt><tt>name</tt>
<dd>The category name will be indexed as the value of a<br>
"Category" property.</dd>
<dt><tt>none</tt>
<dd>No attributes from any of the categories that are applied to<br>
the item are indexed. This value turns off the indexing of<br>
categories.</dd></blockquote>

<blockquote>Specifying an empty string is the same as "all".</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="includedCategories" value="all,searchable" /&gt;<br>
</code></pre></blockquote>

<h4>excludedCategories</h4>
<blockquote>The IDs of the categories that you do not want to index.<br>
Excluding a category means that when that category is applied to<br>
an item, the attributes are <em>not</em> indexed as metadata<br>
for that item.</blockquote>

<blockquote>A comma-separated list of object IDs for categories, or one of<br>
the following special strings:<br>
<dl>
<dt><tt>all</tt>
<dd>All attributes from each of the categories that are applied to<br>
the item are excluded. This value turns off the indexing of<br>
categories.</dd>
<dt><tt>none</tt>
<dd>No attributes from any of the categories that are applied to<br>
the item are excluded. </dd></blockquote>

<blockquote>Specifying an empty string is the same as "none".</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="excludedCategories" value="" /&gt;<br>
</code></pre></blockquote>

<h4>includedSelectExpressions</h4>
<blockquote>The additional SQL <code>SELECT</code> expressions that you want to index.<br>
The expressions are added to the main traversal query<br>
against the WebNodes view and the resulting values are indexed<br>
under the given property name. If the property exists, a new value<br>
will be added to it.</blockquote>

<blockquote>A map from property names to SQL <code>SELECT</code> expressions.</blockquote>

<blockquote>This is similar to adding a property with a document filter, except<br>
that the values here can be based on data from the database.<br>
In the query, the WebNodes view is given a range variable, or<br>
table correlation, of "<code>a</code>".<br>
For example, to index a count of the siblings of each document,<br>
you could include the following entry in the map:<br>
<pre><code>    &lt;entry key="siblingCount"<br>
          value="(select count(*)-1 from DTree d where d.ParentID = a.ParentID)" /&gt;<br>
</code></pre></blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="includedSelectExpressions"&gt;<br>
      &lt;map&gt;&lt;/map&gt;<br>
    &lt;/property&gt;<br>
</code></pre></blockquote>

<blockquote><em>Since:</em> 2.8</blockquote>

<h4>showHiddenItems</h4>
<blockquote>Specifies whether hidden items, or their children, are shown<br>
in the search results. This property is used for authorization,<br>
and not for indexing.</blockquote>

<blockquote>Excluding hidden items, by setting this property to false,<br>
requires the use of the DTreeAncestors table in Livelink, which in<br>
turn requires that the Livelink Recommender agent be enabled. The<br>
latest monthly Livelink patches are recommended. Setting<br>
<a href='AdvancedConfiguration#useDTreeAncestors.md'>useDTreeAncestors</a>
to <tt>false</tt> is not supported with this configuration.</blockquote>

<blockquote>One of the following values:<br>
<dl>
<dt><tt>{}</tt> or <tt>false</tt>
<dd>Do not show hidden items.<br>
<dt><tt>{'ALL'}</tt> or <tt>true</tt>
<dd>Show all hidden items.</dd></blockquote>

<blockquote>This value may be copied from the <tt>ShowHiddenItems</tt>
parameter in the <code>[Explorer]</code> section or in the<br>
<code>[Atlas]</code> section of the opentext.ini file.</blockquote>

<blockquote>Subtype numbers in the list are silently accepted, but ignored.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="showHiddenItems" value="true" /&gt;<br>
</code></pre></blockquote>

<h4>sqlWhereCondition</h4>
<blockquote>The additional SQL <code>WHERE</code> conditions that you want to use to restrict<br>
the items to be indexed. The conditions are added to the main traversal query<br>
against the WebNodes view and may reference any searchable columns in that<br>
view. In the query, the WebNodes view is given a range variable, or<br>
table correlation, of "<code>a</code>".</blockquote>

<blockquote>A SQL predicate.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="sqlWhereCondition" value="" /&gt;<br>
</code></pre></blockquote>

<blockquote><em>Since:</em> 2.8.4</blockquote>

<h4>trackDeletedItems</h4>
<blockquote>Specifies whether to track items that have been deleted from<br>
the Livelink repository, so that those items may also be deleted<br>
from the Google search index.</blockquote>

<blockquote>For optimal performance tracking deleted items, the Livelink<br>
system administrator should add a database index for the AuditID,<br>
AuditDate, and EventID columns of the DAuditNew table of the<br>
Livelink backend database.</blockquote>

<blockquote>Deleting documents from a connector instance that only indexes<br>
a small portion of the repository is inefficient. To reasonably<br>
manage GSA licensing limits, you might consider disabling GSA<br>
deletes for those instances, while leaving them enabled for a<br>
connector instance that indexes the entire repository.</blockquote>

<blockquote>One of the following values:<br>
<dl>
<dt><tt>false</tt>
<dd>Do not track deleted items.<br>
<dt><tt>true</tt>
<dd>Track deleted items, sending delete notifications to the<br>
Google Search Appliance.</dd></blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="trackDeletedItems" value="true" /&gt;<br>
</code></pre></blockquote>

<h4>displayPatterns</h4>
<blockquote>A map of the relative display URLs used in search results.<br>
Each subtype can have a different relative URL. This URL suffix is<br>
combined with the value of the displayUrl property to form a<br>
complete URL for each item in the search results.</blockquote>

<blockquote>The map contains keys that consist of comma-separated subtype<br>
integers, or the special string "default". These are mapped to a<br>
<tt>java.text.MessageFormat</tt> pattern. There are four<br>
parameters that can be used in each pattern, delimited by braces:<br>
<dl>
<dt> 0<br>
<dd> The object ID.</dd>
<dt> 1<br>
<dd> The volume ID.</dd>
<dt> 2<br>
<dd> The subtype.</dd>
<dt> 3<br>
<dd> The display action, which varies by subtype and is configured<br>
by the displayActions property.</dd>
<dt> 4<br>
<dd> The filename with extension, based on the name of the item<br>
and the version filename extension. Intended for use<br>
with <tt>doc.Fetch</tt> URLs (see the example below).</dd></blockquote>

<blockquote>To fetch documents directly from the search results, instead of<br>
linking to the Overview or Properties page, add one of the following<br>
entries to the map:<br>
<pre><code>    &lt;entry key="144" value="/open/{0}" /&gt;<br>
</code></pre>
or<br>
<pre><code>    &lt;entry key="144" value="/{0}/{4}?func=doc.Fetch&amp;amp;nodeid={0}&amp;amp;viewType=1" /&gt;<br>
</code></pre></blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="displayPatterns"&gt;<br>
        &lt;map&gt;<br>
            &lt;entry key="141" value="?func=llworkspace" /&gt;<br>
            &lt;entry key="142"<br>
                   value="?func=ll&amp;amp;objtype=142&amp;amp;objAction=browse" /&gt;<br>
            &lt;entry key="default"<br>
                   value="?func=ll&amp;amp;objId={0}&amp;amp;objAction={3}" /&gt;<br>
        &lt;/map&gt;<br>
    &lt;/property&gt;<br>
</code></pre></blockquote>

<h4>displayActions</h4>
<blockquote>A map of the display actions used in search results.<br>
Each subtype can have a different action.</blockquote>

<blockquote>The map contains keys that consist of comma-separated subtype<br>
integers, or the special string "default". These are mapped to<br>
Livelink action names, such as "browse" or "overview". If the map<br>
contains an entry mapping "144" (documents) to "overview", and the<br>
Livelink server is version 9.5 or earlier, the "properties"<br>
action is used instead.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="displayActions"&gt;<br>
        &lt;map&gt;<br>
            &lt;entry key="0,136,202,402" value="browse" /&gt;<br>
            &lt;entry key="1" value="open" /&gt;<br>
            &lt;entry key="130,134,215" value="view" /&gt;<br>
            &lt;entry key="144" value="overview" /&gt;<br>
            &lt;entry key="204" value="BrowseTaskList" /&gt;<br>
            &lt;entry key="206" value="BrowseTask" /&gt;<br>
            &lt;entry key="207" value="ViewChannel" /&gt;<br>
            &lt;entry key="208" value="ViewNews" /&gt;<br>
            &lt;entry key="218" value="OpenPoll" /&gt;<br>
            &lt;entry key="default" value="properties" /&gt;<br>
        &lt;/map&gt;<br>
    &lt;/property&gt;<br>
</code></pre></blockquote>

<h4>useDTreeAncestors</h4>
<blockquote>Specifies whether to use the DTreeAncestors table to get hierarchy<br>
information. Using the database table is faster, but the table may be<br>
empty or incomplete.</blockquote>

<blockquote>Due to performance issues, Open Text has sometimes recommended turning off the<br>
Recommender agent. There have also been a number of bugs that led to missing entries. Many<br>
of the performance issues and other bugs have been fixed in the latest monthly patches, but it<br>
would be nice not to rely on DTreeAncestors since many deployments have incomplete or missing<br>
data.</blockquote>

<blockquote>If the use of DTreeAncestors is disabled, then a <a href='AdvancedConfiguration#genealogist.md'>Genealogist</a>
implementation class will be used to get hierarchy information.</blockquote>

<blockquote>One of the following values:<br>
<dl>
<dt><tt>false</tt>
<dd>Use multiple, separate database queries to get information about the hierarchy.<br>
<dt><tt>true</tt>
<dd>Use the DTreeAncestors table.</dd></blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="useDTreeAncestors" value="true" /&gt;<br>
</code></pre></blockquote>

<blockquote><em>Since:</em> 2.6.10</blockquote>

<h3>Other Properties</h3>

<h4>authenticationManager</h4>
<blockquote>The <tt>AuthenticationManager</tt> instance to use<br>
when authenticating before providing access to content.</blockquote>

<blockquote>There are three <tt>AuthenticationManager</tt> implementations in the<br>
<tt>com.google.enterprise.connector.otex</tt> package:<br>
<dl>
<dt><tt>LivelinkAuthenticationManager</tt>
<dd>This implementation authenticates by using the<br>
provided credentials to log in to Livelink.<br>
<dt><tt>NoOpAuthenticationManager</tt>
<dd><b>Note: This value bypasses Livelink security.</b>
This implementation authenticates all users. An<br>
optional "sharedPassword" property may be<br>
configured containing a password which must be<br>
present in the credentials.<br>
<dt><tt>LdapAuthenticationManager</tt>
<dd>This implementation uses the provided credentials<br>
to authenticate against an LDAP server. Two<br>
properties are required:</dd>
<blockquote><dl>
<dt><tt>providerUrl</tt>
<dd>The LDAP or LDAPS URL for the directory server.<br>
<dt><tt>securityPrincipalPattern</tt>
<dd>The DN to use for authentication. A single<br>
<tt>java.text.MessageFormat</tt>
parameter should be supplied;<br>
the provided username will be substituted<br>
there. For example:<br>
<pre>uid={0},ou=people,dc=example,dc=com</pre></dd></blockquote></blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="authenticationManager"&gt;<br>
        &lt;bean class="com.google.enterprise.connector.otex.LivelinkAuthenticationManager"/&gt;<br>
    &lt;/property&gt;<br>
</code></pre></blockquote>

<blockquote>In versions prior to 2.6.10, a custom authentication manager was assigned by editing<br>
the <tt>authenticationManager</tt> bean definition in connectorInstance.xml.</blockquote>

<h4>authorizationManager</h4>
<blockquote>The <tt>AuthorizationManager</tt> instance to use<br>
when authoring specific documents for specific users.</blockquote>

<blockquote>There is a single <tt>AuthorizationManager</tt> in the connector,<br>
but custom implementations can be used.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="authorizationManager"&gt;<br>
        &lt;bean class="com.google.enterprise.connector.otex.LivelinkAuthorizationManager"/&gt;<br>
    &lt;/property&gt;<br>
</code></pre></blockquote>

<blockquote><em>Since:</em> 2.6.10</blockquote>

<h4>publicContentAuthorizationManager</h4>
<blockquote>The <tt>AuthorizationManager</tt> instance to use<br>
when assigning public access to documents that are accessible<br>
by the <a href='AdvancedConfiguration#publicContentUsername.md'>publicContentUsername</a> user.<br>
Unlike the <a href='AdvancedConfiguration#authorizationManager.md'>authorizationManager</a>, this<br>
bean must be an instance of <tt>LivelinkAuthorizationManager</tt>.</blockquote>

<blockquote>There is a single <tt>AuthorizationManager</tt> in the connector,<br>
but custom implementations can be used.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="publicContentAuthorizationManager"&gt;<br>
        &lt;bean class="com.google.enterprise.connector.otex.LivelinkAuthorizationManager"/&gt;<br>
    &lt;/property&gt;<br>
</code></pre></blockquote>

<blockquote><em>Since:</em> 2.6.10</blockquote>

<h4>tryLowercaseUsernames</h4>
<blockquote>A hack to try using a lowercase version of the supplied<br>
username for authorizations. If <tt>true</tt>, the lowercase<br>
form of the username will be tried first, and if authorization<br>
fails, the original form of the username will be tried.<br>
If <tt>false</tt>, only the original form of the username will<br>
be tried.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="tryLowercaseUsernames" value="false"/&gt;<br>
</code></pre></blockquote>

<h4>contentHandler</h4>
<blockquote>The name of the <tt>ContentHandler</tt> implementation<br>
class to use when retrieving document content for indexing.</blockquote>

<blockquote>There are four <tt>ContentHandler</tt> implementations in the<br>
<tt>com.google.enterprise.connector.otex</tt> package:<br>
<dl>
<dt><tt>ByteArrayContentHandler</tt>
<dd> The fastest implementation, but not a very scalable one<br>
because it maintains the entire content in a byte array.<br>
<dt><tt>FileContentHandler</tt>
<dd> Stores the content in a temporary file, but performs very<br>
badly for files larger than 10 MB.<br>
<dt><tt>HttpURLContentHandler</tt>
<dd> Retrieves the content directly from the Livelink server using<br>
an <tt>HttpURLConnection</tt>. Performance is inexplicably<br>
disappointing, in line with but consistently 1-1/2 to 3 times slower<br>
than the others.<br>
<dt><tt>PipedContentHandler</tt>
<dd> <b>DO NOT USE:</b> This implementation has several bugs that<br>
prevent its use, including a potential for deadlock and failures in<br>
exception handling that can lead to infinite loops during traversal.</dd></blockquote>

<blockquote>Starting in version 2.8.4, the property value can also be a bean,<br>
in addition to the class name strings. The <tt>HttpURLContentHandler</tt>
supports properties of its own:<br>
<dl>
<dt><tt>urlBase</tt>
<dd> The URL prefix used to retrieve the document. The default value is<br>
the value of the <b>Livelink URL</b> field in the connector configuration page.<br>
<dt><tt>urlPath</tt>
<dd> The URL path used to retrieve the document. The default value is<br>
<code>"?func=ll&amp;objAction=download&amp;objId="</code>.<br>
<dt><tt>connectTimeout</tt>
<dd> The connect timeout in seconds. The default timeout is zero, which<br>
means timeouts are disabled. <em>Since:</em> 3.2.2<br>
<dt><tt>readTimeout</tt>
<dd> The read timeout in seconds. The default timeout is zero, which<br>
means timeouts are disabled. <em>Since:</em> 3.2.2</blockquote>

<blockquote>For example:<br>
<pre><code>    &lt;property name="contentHandler"&gt;<br>
        &lt;bean class="com.google.enterprise.connector.otex.HttpURLContentHandler"&gt;<br>
            &lt;property name="urlPath" value="?func=myfunc&amp;objid="/&gt;<br>
            &lt;property name="readTimeout" value="60"/&gt;<br>
        &lt;/bean&gt;<br>
    &lt;/property&gt;<br>
</code></pre></blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="contentHandler"<br>
              value="com.google.enterprise.connector.otex.FileContentHandler"/&gt;<br>
</code></pre></blockquote>

<h4>servtype</h4>
<blockquote>The database server type.</blockquote>

<blockquote>Valid values begin with "MSSQL" or "Oracle". Sybase is not<br>
supported. This value may be copied from the <tt>servtype</tt>
parameter in the<br>
<code>[dbconnection:</code><em>connection_name</em><code>]</code> section of<br>
the opentext.ini file. If the value is empty the database type will<br>
be determined automatically.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="servtype" value=""/&gt;<br>
</code></pre></blockquote>

<h4>genealogist</h4>
<blockquote>The name of the <tt>Genealogist</tt> implementation<br>
class to use to lookup folder hierarchy information when<br>
<a href='AdvancedConfiguration#useDTreeAncestors.md'>useDTreeAncestors</a>
is <tt>false</tt>.</blockquote>

<blockquote>There are three <tt>Genealogist</tt> implementations in the<br>
<tt>com.google.enterprise.connector.otex</tt> package:<br>
<dl>
<dt><tt>Genealogist</tt>
<dd> The basic implementation, which uses one query for each item in a batch for each<br>
level of the hierarchy.<br>
<dt><tt>HybridGenealogist</tt>
<dd> Uses a single query to get the parents of all items in a batch, and then<br>
uses one query for each item for each additional level of the hierarchy.<br>
<dt><tt>BatchGenealogist</tt>
<dd> Uses one query for each level of the hierarchy.</blockquote>

<blockquote><em>Default Value:</em>
<pre><code>    &lt;property name="genealogist"<br>
              value="com.google.enterprise.connector.otex.BatchGenealogist"/&gt;<br>
</code></pre></blockquote>

<blockquote><em>Since:</em> 2.6.10