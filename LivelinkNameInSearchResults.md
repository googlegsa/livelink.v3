# Introduction #

The Google Search Appliance usually determines the title displayed in the search results from the document content. In many cases, the Livelink object name is a more thoughtful title to display. It is possible to customize the front end stylesheet to display the Livelink object name when it is applicable.

**NOTE: Customizing the stylesheet is not supported in any way by Google for Work Support. Use these modifications at your own risk.**

# Details #

You can edit the XSTL in the front end under **Serving >Front Ends >Output Format**. Note that once you edit the raw XSLT, the Page Layout Helper can no longer be used.

First, to retrieve the google:title metadata element in the front end, find the following lines:
```
  </xsl:if>
</xsl:template>

<!-- *** space_normalized_query: q = /GSP/Q *** -->
```
and add the following text between the first two lines, before the `</xsl:template>` line:
```
  <xsl:if test="not(PARAM[@name = 'getfields'])">
    <input type="hidden" name="getfields" value="google:title"/>
 </xsl:if>
```

Second, to use the google:title element as a title, find the following lines:
```
    <xsl:choose>
      <xsl:when test="T">
```
and insert the following text between them, before the `<xsl:when test="T">` line:
```
      <xsl:when test="MT[@N='google:title']">
        <xsl:call-template name="reformat_keyword">
          <xsl:with-param name="orig_string" select="MT[@N='google:title']/@V"/>
        </xsl:call-template>
      </xsl:when>
```

Third, for some help in debugging, you may want to find the line near the top that says:
```
<xsl:variable name="show_meta_tags">0</xsl:variable>
```
and change the `0` to a `1`. This will tell you at a glance whether you're retrieving the google:title property correctly, and you may even want to try this change and the first one without the second to see what the new titles will be along with the old. You will likely want to set it back to `0` for production.

After you save any stylesheet changes, when you perform a search to test them for the first time, you need to add `&proxyreload=1` to the URL (not the search box) to force the front end to reload your edited stylesheet.