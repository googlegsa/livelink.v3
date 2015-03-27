# Introduction #

Unlike web crawling, the Livelink connector doesn't directly expose the folder hierarchy. If you want to separate content in different collections, or you want to replicate the behavior of Livelink slices, you need to be a little creative.

# Details #

## Collections ##

One idea is to create multiple connector instances, where you specify the folders to index for each connector instance. Then you can put each connector instance in a separate collection, using the googleconnector URLs.  The URLs look like this:

> googleconnector://_connector-name_.localhost/doc?docid=_docid_

So, you can create a separate Livelink connector instance that specifies the object ID of the "manual" folder as the item to index. Suppose we call that connector instance "manual". Then in Crawl and Index > Collections, we can create a collection named "manual\_collection" with an URL pattern of

> ^googleconnector://manual

This works normally. All of the connector instance's content will be available in default\_collection, and only the items in the manual folder will be available in manual\_collection.

You may want to schedule the multiple connectors to run in separate time slices, at least for the initial load. Once everything is indexed you go back to running everything, or depending on your need for timeliness, you could continue to interleave them during the day. The downside is that the scheduling mechanism's smallest time unit is one hour.

One caution is that setting Items to Index requires the use of the DTreeAncestors table in Livelink. In turn, that table requires that the Recommender agent be enabled, and there have historically been a number of bugs with DTreeAncestors. If you aren't already using it, you may want to make sure you have a recent monthly patch, and rebuild your DTreeAncestors table. You can do this automatically in Livelink 9.7. Open Text support can help you with previous versions of Livelink.

## Metadata ##

Another idea is to use metadata to restrict your searches. There is no immediate metadata to search for items in the same folder hierarchy, but here are some possibilities:

**1.** Apply different Livelink Categories to separate folders and in searching using requiredfields in the stylesheet. So if you created a "Manual" category and assigned it to the "manual" folder and its children, and indexed category names (a tweak to connectorInstance.xml) you could use

> requiredfields=category:Manual

in the style sheet or

> inmeta:category=Manual

in the search query.

**2.** If all of the content is contained directly in a single folder, or a small number of folders, then you could add ParentID to the includedObjectInfo in connectorInstance.xml, and restrict the query just like we did with the category. For multiple folders you would need to use the | syntax:

> requiredfields=parentid:123|parentid:456

This wouldn't work for a deep or dynamic hierarchy.

**3.** Use the VolumeID, which is indexed by default. This handles complete hierarchies, but folders aren't volumes. So you would need to put the special collection in something that created a volume, such as a Livelink project. Then you could restrict searches to that project using

> requiredfields=volumeid:-123