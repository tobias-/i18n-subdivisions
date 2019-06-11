#!/usr/bin/env groovy
import com.neovisionaries.i18n.CountryCode
import com.sun.codemodel.*
import groovy.transform.Field
import org.apache.commons.lang3.StringUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements


@Field
private static final String JAVA_PACKAGE = "be.olsson.i18n.subdivision"

@Field
final def JCodeModel cm = new JCodeModel();
@Field
final JClass countryCodeClass = cm.ref(CountryCode.class)
@Field
final JClass countrySubdivisionClass = cm._class("${JAVA_PACKAGE}.CountryCodeSubdivision", ClassType.INTERFACE)

class SubDiv {
    String code;
    String name;
    String source;
}
countrySubdivisionClass.with {
    method(0, String.class, "getCode")
    method(0, CountryCode.class, "getCountryCode")
    method(0, boolean.class, "isRealRegion")
    method(0, String.class, "getName")
    method(0, String.class, "getSource")
}

 Map<String, SubDiv> parseHtmlUnece(CountryCode cc, URL uri, URL sourceUrl) {
    Map<String, SubDiv> parsedData = [:]
    try {
        def html = uri.text
        Document parse = Jsoup.parse(html)
        // Caveat JSoup magic that makes regex seem easy.
        // All <tr> tags from <tbody> (inserted by Jsoup when parsing) from <table>
        // <table> is found by checking if any of the <td> nodes below contains the string "Level"
        Elements rows = parse.select("table:has(td:contains(Level)) > tbody > tr:gt(0)")
        rows.each { row ->
            def newCC = CountryCode.getByCode(trim(row.child(0).text()), false);
            if (cc != null && cc != newCC) {
                throw new RuntimeException("For ${uri}, expected (Country=${cc}) but found (Country=${newCC})")
            }
            def subDivisionCode = trim(row.child(1).text())
            def subDivisionName = trim(row.child(2).text().replaceFirst(/\(.+separate entry[^\)]+\)/,""))
            SubDiv subDiv = new SubDiv()
            subDiv.code = subDivisionCode
            subDiv.name = subDivisionName
            subDiv.source = sourceUrl.text
            parsedData[subDivisionCode] = subDiv
        }
    } catch (FileNotFoundException ignored) {
    }
     return parsedData
 }

Map<String, SubDiv> parseHtmlWiki(CountryCode cc, URL uri, URL sourceUrl) {
    Map<String, String> parsedData = [:]
    try {
        def html = uri.text
        Document parse = Jsoup.parse(html)

        Elements rows = parse.select("table.wikitable > tbody > tr:gt(0)")
        rows.each { row ->
            //System.out.println("" + row)
            def newCode = row.select("> td:nth-child(1) > span").text()
            //System.out.println("" + newCode)
            if (newCode != null) {
                def parts = newCode.split('-', 2)
                def newCC = CountryCode.getByCode(parts[0], false);

                if (cc != null && cc != newCC) {
                    //System.out.println("For ${uri}, expected (Country=${cc}) but found (Country=${newCC})")

                } else {
                    // some fuzzy logic, not every wiki page is the same
                    def subDivisionCode = parts[1]
                    def link = row.select("> td:nth-child(2) > a")
                    if (link == null) {
                        link = row.select("> td:nth-child(3) > a")
                    }
                    def subDivisionName = trim(link.text())
                    if (subDivisionName == "") {
                        System.err.println("Name not found in " + row)
                    } else {
                        SubDiv sub = new SubDiv()
                        sub.code = subDivisionCode
                        sub.name= subDivisionName
                        sub.source = sourceUrl.text
                        parsedData[subDivisionCode] = sub
                    }
                }
            }
        }
    } catch (FileNotFoundException ignored) {
    }
    return parsedData
}

JClass parseHtml(CountryCode cc, URL uneceUri, URL sourceUri,  URL wikiUri, URL wikiSourceUri) {
    Map<String, SubDiv> parsedData = [:]

    parsedData.putAll(parseHtmlUnece(cc, uneceUri, sourceUri))
    parseHtmlWiki(cc, wikiUri, wikiSourceUri).forEach{k, v ->
        if (! parsedData.containsKey(k)) {
            System.out.println("Found in wiki, but not in unece " + cc + " " + k + " = " + v)
            parsedData.put(k, v)
        }
    }
    generateClass(cc, parsedData)
}


JClass generateClass(CountryCode countryCode, Map<String, SubDiv> parsedData) {
    JDefinedClass dc = cm._class(0, "${JAVA_PACKAGE}.Subdivision${countryCode.alpha2}", ClassType.ENUM)
    dc._implements(countrySubdivisionClass)
    JFieldVar name = dc.field(JMod.PRIVATE | JMod.FINAL, String.class, "name")
    JFieldVar code = dc.field(JMod.PRIVATE | JMod.FINAL, String.class, "code")
    JFieldVar source = dc.field(JMod.PRIVATE | JMod.FINAL, String.class, "source")
    dc.method(JMod.PUBLIC, CountryCode.class, "getCountryCode").with {
        //annotate(Override.class)
        body().with {
            _return(countryCodeClass.staticRef(countryCode.alpha2))
        }
    }
    dc.method(JMod.PUBLIC, String.class, "getCode").with {
        //annotate(Override.class)
        body().with {
            _return(code);
        }
    }
    dc.method(JMod.PUBLIC, String.class, "getName").with {
        //annotate(Override.class)
        body().with {
            _return(name)
        }
    }
    dc.method(JMod.PUBLIC, String.class, "getSource").with {
        //annotate(Override.class)
        body().with {
            _return(source)
        }

    }
    dc.constructor(0).with {
        def subDivName = param(String.class, "subDivisionName")
        def subDivCode = param(String.class, "subDivisionCode")
        def subDivSource = param(String.class, "subDivisionSource")

        body().with {
            assign(JExpr._this().ref(name), subDivName)
            assign(JExpr._this().ref(code), subDivCode)
            assign(JExpr._this().ref(source), subDivSource)
        }
    }

    if (parsedData) {
        parsedData.each { subDivisionCode, subDiv ->
            String escapedCode = subDiv.code
            if (Character.valueOf(escapedCode.charAt(0)).isDigit()) {
                escapedCode = "_" + escapedCode;
            }
            dc.enumConstant(escapedCode).with {
                arg(JExpr.lit(subDiv.name))
                arg(JExpr.lit(subDiv.code))
                arg(JExpr.lit(subDiv.source))

            }
        }
        dc.method(JMod.PUBLIC, boolean.class, "isRealRegion").with {
            body().with {
                _return(JExpr.lit(true))
            }
        }
    } else {
        dc.enumConstant("NA").with {
            arg(JExpr.lit("No Subdivisions"))
            arg(JExpr.lit("NA"))
            arg(JExpr._null())
        }
        dc.method(JMod.PUBLIC, boolean.class, "isRealRegion").with {
            body().with {
                _return(JExpr.lit(false))
            }
        }
    }
    dc
}

String trim(String str) {
    StringUtils.trim(StringUtils.normalizeSpace(str));
}

Map<CountryCode, JClass> classes = [:]
CountryCode.values().each {
    classes[it] = parseHtml(it,
            new URL("file://${properties.buildresources}${it.alpha2}.html"),
            new URL("file://${properties.buildresources}${it.alpha2}.url"),
            new URL("file://${properties.buildresources}${it.alpha2}.wiki.html"),
            new URL("file://${properties.buildresources}${it.alpha2}.wiki.url")
    )

}

cm._class(JMod.PUBLIC | JMod.FINAL, "${JAVA_PACKAGE}.SubdivisionFactory", ClassType.CLASS).with { factoryClass ->
    def narrowListClass = cm.ref(List.class).narrow(countrySubdivisionClass)
    def narrowArrayListClass = cm.ref(ArrayList.class).narrow(countrySubdivisionClass)
    def arraysClass = cm.ref(Arrays.class)

    def narrowMapClass = cm.ref(Map.class).narrow(countryCodeClass, narrowListClass)
    def narrowHashMapClass = cm.ref(HashMap.class).narrow(countryCodeClass, narrowListClass)
    def map = field(JMod.PRIVATE | JMod.STATIC | JMod.FINAL, narrowMapClass, "map", )

    init().with {
        def initMap = decl(narrowMapClass, "initMap", JExpr._new(narrowHashMapClass))
        classes.each { code, clazz ->
            def countryCodeRef = countryCodeClass.staticRef(code.alpha2)
            add(initMap.invoke("put").with {
                arg(countryCodeRef)
                arg(JExpr._new(narrowArrayListClass).arg(arraysClass.staticInvoke("asList").arg(clazz.staticInvoke("values"))))
            })
        }
        assign(map, cm.ref(Collections.class).staticInvoke("unmodifiableMap").arg(initMap));

    }

    method(JMod.STATIC | JMod.PUBLIC, narrowListClass, "getSubdivisions").with {
        def param1 = param(countryCodeClass, "countryCode")
        body().with {
            _return(map.invoke("get").arg(param1))
        }
    }

    method(JMod.STATIC | JMod.PUBLIC, countrySubdivisionClass, "getSubdivision").with {
        def param1 = param(countryCodeClass, "countryCode")
        def param2 = param(String.class, "subdivisionCodeName")
        body().with {
            def forEach = forEach(countrySubdivisionClass, "subDivisionCode", map.invoke("get").arg(param1))
            forEach.body().with {
                _if(forEach.var().invoke("getCode").invoke("equals").arg(param2))._then().with {
                    _return(forEach.var())
                }
            }
            _return(JExpr._null())
        }
    }
}



def outputDir = new File(properties["subdivision.java.sources"] as String)
outputDir.mkdirs()
cm.build(outputDir)
