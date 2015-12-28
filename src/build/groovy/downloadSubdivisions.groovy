import com.neovisionaries.i18n.CountryCode

File buildResources = new File(properties.buildresources as String)
buildResources.mkdirs()
CountryCode.values().each {
    if (it.assignment == CountryCode.Assignment.OFFICIALLY_ASSIGNED) {
        String html = null
        try {
            html = new URL("http://www.unece.org/fileadmin/DAM/cefact/locode/Subdivision/${it.alpha2.toLowerCase(Locale.US)}Sub.htm").text
        } catch (IOException e) {
            println(e.getMessage())
        }
        if (html) {
            new File(buildResources, "${it.alpha2}.html").withWriter {
                it.write(html)
            }
        }
    }
}