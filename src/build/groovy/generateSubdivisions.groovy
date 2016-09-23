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


countrySubdivisionClass.with {
    method(0, String.class, "getCode")
    method(0, CountryCode.class, "getCountryCode")
    method(0, boolean.class, "isRealRegion")
    method(0, String.class, "getName")
}

JClass parseHtml(CountryCode cc, URL uri) {
    Map<String, String> parsedData = [:]
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
            parsedData[subDivisionCode] = subDivisionName;
        }
    } catch (FileNotFoundException ignored) {
    }
    generateClass(cc, parsedData)
}


JClass generateClass(CountryCode countryCode, Map<String, String> parsedData) {
    JDefinedClass dc = cm._class(0, "${JAVA_PACKAGE}.Subdivision${countryCode.alpha2}", ClassType.ENUM)
    dc._implements(countrySubdivisionClass)
    JFieldVar name = dc.field(JMod.PUBLIC, String.class, "name")
    JFieldVar code = dc.field(JMod.PUBLIC, String.class, "code")
    dc.method(JMod.PUBLIC, CountryCode.class, "getCountryCode").with {
        body().with {
            _return(countryCodeClass.staticRef(countryCode.alpha2))
        }
    }
    dc.method(JMod.PUBLIC, String.class, "getCode").with {
        body().with {
            _return(code);
        }
    }
    dc.method(JMod.PUBLIC, String.class, "getName").with {
        body().with {
            _return(name)
        }
    }
    dc.constructor(0).with {
        def subDivName = param(String.class, "subDivisionName")
        def subDivCode = param(String.class, "subDivisionCode")
        body().with {
            assign(JExpr._this().ref(name), subDivName)
            assign(JExpr._this().ref(code), subDivCode)
        }
    }

    if (parsedData) {
        parsedData.each { subDivisionCode, subDivisionName ->
            String escapedCode = subDivisionCode
            if (Character.valueOf(escapedCode.charAt(0)).isDigit()) {
                escapedCode = "_" + escapedCode;
            }
            dc.enumConstant(escapedCode).with {
                arg(JExpr.lit(subDivisionName))
                arg(JExpr.lit(subDivisionCode))
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
    classes[it] = parseHtml(it, new URL("file://${properties.buildresources}/${it.alpha2}.html"))
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
