import com.neovisionaries.i18n.CountryCode

File buildResources = new File(properties.buildresources as String)
buildResources.mkdirs()
CountryCode.values().each {
    if (it.assignment == CountryCode.Assignment.OFFICIALLY_ASSIGNED) {
        String html = null
        URL url = new URL("https://www.unece.org/fileadmin/DAM/cefact/locode/Subdivision/${it.alpha2.toLowerCase(Locale.US)}Sub.htm")

        try {
            html = url.getText("UTF-8")
        } catch (IOException e) {
            println(e.getMessage())
        }
        if (html) {
            new File(buildResources, "${it.alpha2}.html").withWriter("UTF-8", {
                it.write(html)
            })
            new File(buildResources, "${it.alpha2}.url").withWriter("UTF-8", {
                it.write(url.toExternalForm())
            })
        }
        URL wikiUrl = new URL("https://en.wikipedia.org/wiki/ISO_3166-2:" + it.alpha2)
        try {
            wikiHtml = wikiUrl.getText("UTF-8")
        } catch (IOException e) {
            println(e.getMessage())
        }
        if (wikiHtml) {
            new File(buildResources, "${it.alpha2}.wiki.html").withWriter("UTF-8", {
                it.write(wikiHtml)
            })
            new File(buildResources, "${it.alpha2}.wiki.url").withWriter("UTF-8", {
                it.write(wikiUrl.toExternalForm())
            })
        }
    }
}
