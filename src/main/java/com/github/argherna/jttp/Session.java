package com.github.argherna.jttp;

import static java.lang.String.format;
import static java.lang.System.Logger.Level.WARNING;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Session object that loads and saves session data.
 * 
 * <p>
 * Session data is saved by default to
 * {@code ${user.home}/.jttp/sessions/HOST_PORT/SESSIONNAME.zip}. The zip file consists of 2 files:
 * <ul>
 * <li>{@code headers.xml} for request headers.
 * <li>{@code cookies.xml} for cookies.
 * </ul>
 */
class Session {

    private static final Boolean DELETE_TEMPFILES = !AccessController.doPrivileged(
            (PrivilegedAction<Boolean>) () -> Boolean.getBoolean(Jttp.SYS_PROP_KEEP_TEMP_FILES));

    private static final Collection<String> RESTRICTED_HEADERS =
            Set.of("Host", "Connection", "Set-Cookie");

    private static final Integer DEFAULT_INDENT = 2;

    private static final Integer INDENT =
            AccessController.doPrivileged((PrivilegedAction<Integer>) () -> Integer
                    .getInteger(Jttp.SYS_PROP_INDENT, DEFAULT_INDENT));

    private static final Map<String, String> LOAD_ENV = Map.of();

    private static final Map<String, String> SAVE_ENV = Map.of("create", "true");

    private final URI sessionFsUri;

    private final HttpURLConnection conn;

    // Maybe make this an option later, for now hardcode as on.
    private final boolean formatSessionXml = true;

    private Document history;

    Session(String sessionName, HttpURLConnection conn) throws URISyntaxException {
        this.sessionFsUri = initSessionFsUri(sessionName, conn.getURL().toURI());
        this.conn = conn;
    }

    /**
     * Load and populate session data into the different parts of the request.
     * 
     * <p>
     * If the session file doesn't yet exist, then this method does nothing. If an IOException
     * occurs and the session file exists, then it is thrown.
     * 
     * @throws IOException                  if an IOException occurs.
     * @throws XMLStreamException           if an XMLStreamException occurs.
     * @throws SAXException                 if a SAXException occurs.
     * @throws ParserConfigurationException if a ParserConfigurationException occurs.
     */
    void load() throws XMLStreamException, ParserConfigurationException, SAXException, IOException {
        // Load headers into HttpURLConnection request properties
        // Set cookies in default CookieHandler's CookieStore.
        try (var sessionFs = FileSystems.newFileSystem(sessionFsUri, LOAD_ENV)) {
            history = getHistoryXml(sessionFs);
            doLoadCookies(sessionFs);
            doLoadHeaders(sessionFs);
        } catch (FileSystemNotFoundException e) {
            // No filesystem available since this is the first time using this so just create
            // the starter history Xml document.
            history = generateNewHistoryDocument();
        }
    }

    /**
     * Save session data.
     * 
     * @param requestData data sent as part of the request, possibly {@code null}.
     * @param response    response from the server.
     * 
     * @throws IOException                          if an IOException occurs.
     * @throws XMLStreamException                   if an XMLStreamException occurs.
     * @throws TransformerException                 if a TransformerException occurs.
     * @throws TransformerFactoryConfigurationError if a TransformerFactoryConfigurationError
     *                                              occurs.
     */
    void save(String requestData, File response) throws IOException, XMLStreamException,
            TransformerFactoryConfigurationError, TransformerException {
        var timeOfRun = Instant.now();
        // Create the filesystem
        try (var sessionFs = FileSystems.newFileSystem(sessionFsUri, SAVE_ENV)) {
            doSaveCookies(sessionFs);
            doSaveHeaders(sessionFs);
            var entryId = doUpdateAndSaveHistory(sessionFs, requestData, timeOfRun);
            doSaveResponseData(sessionFs, response, timeOfRun, entryId);
        }
    }

    /**
     * Loads a file named {@code cookies.xml} in the session zip file.
     * 
     * <p>
     * Cookies are added to the HttpURLConnection in this Session.
     * 
     * @param sessionFs the pseudo FileSystem of the zip file.
     * @throws IOException        if an IOException occurs.
     * @throws XMLStreamException if an XMLStreamException.
     */
    private void doLoadCookies(FileSystem sessionFs) throws IOException, XMLStreamException {
        var cookiesXml = sessionFs.getPath("/cookies.xml");
        try (var infile = cookiesXml.toUri().toURL().openStream()) {
            var xmlIf = XMLInputFactory.newInstance();
            var xmlEvR = xmlIf.createXMLEventReader(infile);
            var qCNm = new QName("name");
            var currEltNm = "";
            var inCookie = false;
            HttpCookie c = null;
            while (xmlEvR.hasNext()) {
                var xmlEv = xmlEvR.nextEvent();
                if (xmlEv.isStartElement()) {
                    var se = xmlEv.asStartElement();
                    currEltNm = se.getName().getLocalPart();
                    if (currEltNm.equals("cookie")) {
                        c = new HttpCookie(se.getAttributeByName(qCNm).getValue(), "");
                        inCookie = true;
                    } else if (inCookie && currEltNm.equals("value")) {
                        xmlEv = xmlEvR.nextEvent();
                        if (xmlEv.isCharacters() && nonNull(c)) {
                            c.setValue(xmlEv.asCharacters().getData().trim());
                        }
                    } else if (inCookie && currEltNm.equals("path")) {
                        xmlEv = xmlEvR.nextEvent();
                        if (xmlEv.isCharacters() && nonNull(c)) {
                            c.setPath(xmlEv.asCharacters().getData().trim());
                        }
                    } else if (inCookie && currEltNm.equals("secure")) {
                        xmlEv = xmlEvR.nextEvent();
                        if (xmlEv.isCharacters() && nonNull(c)) {
                            c.setSecure(Boolean.valueOf(xmlEv.asCharacters().getData().trim()));
                        }
                    } else if (inCookie && currEltNm.equals("comment")) {
                        xmlEv = xmlEvR.nextEvent();
                        if (xmlEv.isCharacters() && nonNull(c)) {
                            c.setComment(xmlEv.asCharacters().getData().trim());
                        }
                    } else if (inCookie && currEltNm.equals("commentUrl")) {
                        xmlEv = xmlEvR.nextEvent();
                        if (xmlEv.isCharacters() && nonNull(c)) {
                            c.setCommentURL(xmlEv.asCharacters().getData().trim());
                        }
                    } else if (inCookie && currEltNm.equals("discard")) {
                        xmlEv = xmlEvR.nextEvent();
                        if (xmlEv.isCharacters() && nonNull(c)) {
                            c.setDiscard(Boolean.valueOf(xmlEv.asCharacters().getData().trim()));
                        }
                    } else if (inCookie && currEltNm.equals("max_age")) {
                        xmlEv = xmlEvR.nextEvent();
                        if (xmlEv.isCharacters() && nonNull(c)) {
                            c.setMaxAge(Long.valueOf(xmlEv.asCharacters().getData().trim()));
                        }
                    } else if (inCookie && currEltNm.equals("version")) {
                        xmlEv = xmlEvR.nextEvent();
                        if (xmlEv.isCharacters() && nonNull(c)) {
                            c.setVersion(Integer.valueOf(xmlEv.asCharacters().getData().trim()));
                        }
                    } else if (inCookie && currEltNm.equals("http_only")) {
                        xmlEv = xmlEvR.nextEvent();
                        if (xmlEv.isCharacters() && nonNull(c)) {
                            c.setHttpOnly(Boolean.valueOf(xmlEv.asCharacters().getData().trim()));
                        }
                    } else if (inCookie && currEltNm.equals("port_list")) {
                        xmlEv = xmlEvR.nextEvent();
                        if (xmlEv.isCharacters() && nonNull(c)) {
                            c.setPortlist(xmlEv.asCharacters().getData().trim());
                        }
                    }
                } else if (xmlEv.isEndElement()) {
                    var ee = xmlEv.asEndElement();
                    if (ee.getName().getLocalPart().equals("cookie")) {
                        inCookie = false;
                        if (!c.hasExpired()) {
                            conn.addRequestProperty("Cookie", c.toString());
                        }
                        c = null;
                    }
                }
            }
        }
    }

    /**
     * Loads a file named {@code headers.xml} in the session zip file.
     * 
     * <p>
     * Headers in the XML file are added to the HttpURLConnection in this Session.
     * 
     * @param sessionFs the pseudo FileSystem of the zip file.
     * @throws IOException        if an IOException occurs.
     * @throws XMLStreamException if an XMLStreamException.
     */
    private void doLoadHeaders(FileSystem sessionFs) throws IOException, XMLStreamException {
        var headersXml = sessionFs.getPath("/headers.xml");
        try (var infile = headersXml.toUri().toURL().openStream()) {
            var xmlIf = XMLInputFactory.newInstance();
            var xmlEvR = xmlIf.createXMLEventReader(infile);
            var qNm = new QName("name");
            var currEltNm = "";
            var hdrNm = "";
            var hdrVls = new ArrayList<String>();
            while (xmlEvR.hasNext()) {
                var xmlEv = xmlEvR.nextEvent();
                if (xmlEv.isStartElement()) {
                    var se = xmlEv.asStartElement();
                    currEltNm = se.getName().getLocalPart();
                    if (currEltNm.equals("header")) {
                        hdrNm = se.getAttributeByName(qNm).getValue();
                    } else if (currEltNm.equals("value")) {
                        xmlEv = xmlEvR.nextEvent();
                        if (xmlEv.isCharacters()) {
                            hdrVls.add(xmlEv.asCharacters().getData().trim());
                        }
                    }
                } else if (xmlEv.isEndElement()) {
                    var ee = xmlEv.asEndElement();
                    if (ee.getName().getLocalPart().equals("header")) {
                        conn.setRequestProperty(hdrNm, hdrVls.get(0));
                        for (String hdrVl : hdrVls.subList(1, hdrVls.size())) {
                            conn.addRequestProperty(hdrNm, hdrVl);
                        }
                        hdrVls.clear();
                    }
                }
            }
        }
    }

    /**
     * Instantiates the history document for this Session.
     * 
     * <p>
     * First, try to read an existing history document on the Session FileSystem. Failing that,
     * generate a new one.
     * 
     * @param sessionFs the Session FileSystem.
     * 
     * @return Document containing history data.
     * 
     * @throws IOException                  if an IOException occurs.
     * @throws MalformedURLException        if a MalformedURLException occurs.
     * @throws ParserConfigurationException if a ParserConfigurationException occurs.
     * @throws SAXException                 if a SAXException occurs.
     */
    private Document getHistoryXml(FileSystem sessionFs)
            throws ParserConfigurationException, MalformedURLException, SAXException, IOException {
        var historyXml = sessionFs.getPath("/history.xml");
        Document histDoc = null;
        var dbf = DocumentBuilderFactory.newInstance();
        var histDb = dbf.newDocumentBuilder();
        if (Files.exists(historyXml)) {
            try (var xmlIn = historyXml.toUri().toURL().openStream()) {
                histDoc = histDb.parse(xmlIn);
            } catch (Exception e) {
                // Something's wrong so log it and generate a new history document.
                Jttp.LOGGER.log(WARNING, "logger.warning.xml.history.error", e);
                generateNewHistoryDocument(histDb);
            }
        } else {
            histDoc = generateNewHistoryDocument(histDb);
        }
        return histDoc;
    }

    /**
     * @return Document containing root element for history data.
     */
    private Document generateNewHistoryDocument() throws ParserConfigurationException {
        var dbf = DocumentBuilderFactory.newInstance();
        return generateNewHistoryDocument(dbf.newDocumentBuilder());
    }

    /**
     * @param db a DocumentBuilder.
     * @return Document containing root element for history data.
     */
    private Document generateNewHistoryDocument(DocumentBuilder db) {
        var histDoc = db.newDocument();
        var root = histDoc.createElement("jttp_history");
        histDoc.appendChild(root);
        return histDoc;
    }

    /**
     * Adds an entry with the appropriate ID and saves it to the Session FileSystem.
     * 
     * @param sessionFs   FileSystem to write the history document to.
     * @param requestData data sent by the request, possibly null or empty.
     * @param timeOfRun   Instant the request is being recorded.
     * 
     * @return the ID of the entry appended to the history.
     * 
     * @throws IOException                          if an IOException occurs.
     * @throws TransformerFactoryConfigurationError if a TransformerFactoryConfigurationError
     *                                              occurs.
     * @throws TransformerException                 if a TransformerException occurs.
     */
    private int doUpdateAndSaveHistory(FileSystem sessionFs, String requestData, Instant timeOfRun)
            throws IOException, TransformerFactoryConfigurationError, TransformerException {
        var root = history.getDocumentElement(); // jttp_history
        var id = 1;
        var elts = root.getElementsByTagName("entry");
        if (nonNull(elts) && elts.getLength() > 0) {
            for (int i = 0; i < elts.getLength(); i++) {
                var entry = (Element) elts.item(i);
                var entryId = Integer.valueOf(entry.getAttribute("id"));
                id = Math.max(id, entryId);
            }
            id++;
        }
        root.appendChild(createEntry(id, requestData, timeOfRun));
        writeHistory(sessionFs);
        return id;
    }

    /**
     * Creates an Entry in the history document.
     * 
     * @param id          the ID for the entry.
     * @param requestData data sent by the request, possibly null or empty.
     * @param timeOfRun   Instant the request is being recorded.
     * @return the entry Node.
     * 
     * @throws IOException if an IOException occurs.
     */
    private Node createEntry(int id, String requestData, Instant timeOfRun) throws IOException {
        var entry = history.createElement("entry");
        entry.setAttribute("id", Integer.toString(id));
        entry.setAttribute("timestamp", Long.toString(timeOfRun.toEpochMilli()));

        var request = history.createElement("request");

        var method = history.createElement("method");
        var methodText = history.createTextNode(conn.getRequestMethod());
        method.appendChild(methodText);
        request.appendChild(method);

        var uri = history.createElement("uri");
        var uriText = history.createTextNode(conn.getURL().getPath());
        uri.appendChild(uriText);
        request.appendChild(uri);

        if (nonNull(conn.getURL().getQuery())) {
            var query = history.createElement("query");
            var queryString = history.createCDATASection(conn.getURL().getQuery());
            query.appendChild(queryString);
            request.appendChild(query);
        }

        if (nonNull(requestData) && !requestData.isEmpty() && !requestData.isBlank()) {
            var data = history.createElement("data");
            var dataString = history.createCDATASection(requestData);
            data.appendChild(dataString);
            request.appendChild(data);
        }
        entry.appendChild(request);

        var response = history.createElement("response");

        var status = history.createElement("status");
        var statuscode = history.createTextNode(Integer.toString(conn.getResponseCode()));
        status.appendChild(statuscode);
        response.appendChild(status);

        if (nonNull(conn.getHeaderField("Content-Type"))) {
            var contentType = history.createElement("content_type");
            var contentTypeString = history.createTextNode(conn.getHeaderField("Content-Type"));
            contentType.appendChild(contentTypeString);
            response.appendChild(contentType);
        }

        entry.appendChild(response);
        return entry;
    }

    /**
     * Writes the history to the Session FileSystem.
     * 
     * @param sessionFs where to write the history.
     * 
     * @throws IOException                          if an IOException occurs.
     * @throws TransformerFactoryConfigurationError if a TransformerFactoryConfigurationError
     *                                              occurs.
     * @throws TransformerException                 if a TransformerException occurs.
     */
    private void writeHistory(FileSystem sessionFs)
            throws TransformerFactoryConfigurationError, TransformerException, IOException {
        var historyXml = File.createTempFile("history", ".xml");
        if (DELETE_TEMPFILES) {
            historyXml.deleteOnExit();
        }

        var formatted = Util.formatMarkup(new DOMSource(history));
        var historyXmlAsPath = Paths.get(historyXml.toURI());
        Files.writeString(historyXmlAsPath, formatted, CREATE);
        var historyXmlInSession = sessionFs.getPath("/history.xml");
        Files.copy(historyXmlAsPath, historyXmlInSession, REPLACE_EXISTING);
    }

    /**
     * Writes the response data to a file in the Session FileSystem.
     * 
     * <p>
     * File name has the format {@code entry-[ID]-[timeOfRunInMillis]}.
     * 
     * @param sessionFs the Session FileSystem.
     * @param response  the response data File.
     * @param timeOfRun Instant the request is being recorded.
     * @param entryId   Id for the entry.
     * 
     * @throws IOException if an IOException occurs.
     */
    private void doSaveResponseData(FileSystem sessionFs, File response, Instant timeOfRun,
            int entryId) throws IOException {
        var responseAsPath = Paths.get(response.toURI());
        var responseInSession =
                sessionFs.getPath(format("/entry-%d-%d", entryId, timeOfRun.toEpochMilli()));
        Files.copy(responseAsPath, responseInSession, REPLACE_EXISTING);
    }

    /**
     * Save a file named {@code cookies.xml} in the session zip file.
     * 
     * @param sessionFs the psuedo FileSystem of the zip file.
     * @throws IOException        if an IOException occurs.
     * @throws XMLStreamException if an XMLStreamException.
     */
    private void doSaveCookies(FileSystem sessionFs) throws IOException, XMLStreamException {
        var cookiesXml = File.createTempFile("cookies", ".xml");
        if (DELETE_TEMPFILES) {
            cookiesXml.deleteOnExit();
        }
        // Go through the response headers, filter out the Set-Cookie header(s), create
        // a flat List containing only the values from the Set-Cookie header(s).
        var setCookies = conn.getHeaderFields().entrySet().stream()
                .filter(e -> "Set-Cookie".equals(e.getKey())).map(e -> e.getValue())
                .collect(toList()).stream().flatMap(List::stream).collect(toList());
        // Check the request headers as well and add those too.
        setCookies.addAll(conn.getRequestProperties().entrySet().stream()
                .filter(e -> "Cookie".equals(e.getKey())).map(e -> e.getValue()).collect(toList())
                .stream().flatMap(List::stream).collect(toList()));

        try (var outfile = new FileWriter(cookiesXml)) {
            var xmlOf = XMLOutputFactory.newInstance();
            var xsw = xmlOf.createXMLStreamWriter(outfile);
            xsw.writeStartDocument("utf-8", "1.0");
            doFormat(xsw, 0);
            if (setCookies.isEmpty()) {
                xsw.writeEmptyElement("jttp_cookies");
            } else {
                xsw.writeStartElement("jttp_cookies");
                var indentLevel = 1;
                doFormat(xsw, indentLevel);
                for (int i = 0; i < setCookies.size(); i++) {
                    var setCookie = setCookies.get(i);
                    xsw.writeStartElement("cookies");
                    doFormat(xsw, ++indentLevel);
                    var httpCookies = HttpCookie.parse(setCookie);
                    for (var httpCookie : httpCookies) {
                        xsw.writeStartElement("cookie");
                        xsw.writeAttribute("name", httpCookie.getName());
                        doFormat(xsw, ++indentLevel);
                        if (nonNull(httpCookie.getPath()) && !httpCookie.getPath().isEmpty()) {
                            writeXmlElement(xsw, "path", httpCookie.getPath());
                        }

                        doFormat(xsw, indentLevel);
                        writeXmlElement(xsw, "secure", Boolean.toString(httpCookie.getSecure()));

                        doFormat(xsw, indentLevel);
                        writeXmlElement(xsw, "value", httpCookie.getValue());

                        if (nonNull(httpCookie.getComment())
                                && !httpCookie.getComment().isEmpty()) {
                            doFormat(xsw, indentLevel);
                            writeXmlElement(xsw, "comment", httpCookie.getComment());
                        }

                        if (nonNull(httpCookie.getCommentURL())
                                && !httpCookie.getCommentURL().isEmpty()) {
                            doFormat(xsw, indentLevel);
                            writeXmlElement(xsw, "commentUrl", httpCookie.getCommentURL());
                        }

                        doFormat(xsw, indentLevel);
                        writeXmlElement(xsw, "discard", Boolean.toString(httpCookie.getDiscard()));

                        doFormat(xsw, indentLevel);
                        writeXmlElement(xsw, "max_age", Long.toString(httpCookie.getMaxAge()));

                        doFormat(xsw, indentLevel);
                        writeXmlElement(xsw, "version", Integer.toString(httpCookie.getVersion()));

                        doFormat(xsw, indentLevel);
                        writeXmlElement(xsw, "http_only",
                                Boolean.toString(httpCookie.isHttpOnly()));

                        if (nonNull(httpCookie.getPortlist())
                                && !httpCookie.getPortlist().isEmpty()) {
                            doFormat(xsw, indentLevel);
                            writeXmlElement(xsw, "port_list", httpCookie.getPortlist());
                        }

                        if (nonNull(httpCookie.getDomain()) && !httpCookie.getDomain().isEmpty()) {
                            doFormat(xsw, indentLevel);
                            writeXmlElement(xsw, "domain", httpCookie.getDomain());
                        }
                        doFormat(xsw, --indentLevel);
                        xsw.writeEndElement(); // cookie
                    }
                    doFormat(xsw, --indentLevel);
                    xsw.writeEndElement();
                    doFormat(xsw, i < setCookies.size() - 1 ? indentLevel : 0);
                }
            }
            xsw.writeEndDocument();
        }
        var cookiesXmlAsPath = Paths.get(cookiesXml.toURI());
        var cookiesXmlInSession = sessionFs.getPath("/cookies.xml");
        Files.copy(cookiesXmlAsPath, cookiesXmlInSession, REPLACE_EXISTING);
    }

    /**
     * Save a file named {@code headers.xml} in the session zip file.
     * 
     * @param sessionFs the psuedo FileSystem of the zip file.
     * @throws IOException        if an IOException occurs.
     * @throws XMLStreamException if an XMLStreamException occurs.
     */
    private void doSaveHeaders(FileSystem sessionFs) throws IOException, XMLStreamException {
        var headersXml = File.createTempFile("headers", ".xml");
        if (DELETE_TEMPFILES) {
            headersXml.deleteOnExit();
        }
        try (var outfile = new FileWriter(headersXml)) {
            var xmlOf = XMLOutputFactory.newInstance();
            XMLStreamWriter xsw = xmlOf.createXMLStreamWriter(outfile);
            xsw.writeStartDocument("utf-8", "1.0");
            doFormat(xsw, 0);
            if (conn.getRequestProperties().isEmpty()) {
                xsw.writeEmptyElement("jttp_headers");
            } else {
                xsw.writeStartElement("jttp_headers");
                var indentLevel = 1;
                doFormat(xsw, indentLevel);
                var reqProps = conn.getRequestProperties().entrySet().stream()
                        .filter(e -> !RESTRICTED_HEADERS.contains(e.getKey())
                                && nonNull(e.getValue().get(0)))
                        .collect(toMap(e -> e.getKey(), e -> e.getValue()));
                for (Iterator<String> iter0 = reqProps.keySet().iterator(); iter0.hasNext();) {
                    var header = iter0.next();
                    var values = reqProps.get(header);
                    if (!values.isEmpty()) {
                        xsw.writeStartElement("header");
                        xsw.writeAttribute("name", header);
                        doFormat(xsw, ++indentLevel);
                        xsw.writeStartElement("values");
                        doFormat(xsw, ++indentLevel);
                        for (Iterator<String> iter1 = values.iterator(); iter1.hasNext();) {
                            writeXmlElement(xsw, "value", iter1.next());
                            if (iter1.hasNext()) {
                                doFormat(xsw, indentLevel);
                            }
                        }
                        doFormat(xsw, --indentLevel);
                        xsw.writeEndElement(); // values
                        doFormat(xsw, --indentLevel);
                        xsw.writeEndElement(); // header
                        if (iter0.hasNext()) {
                            doFormat(xsw, indentLevel);
                        }
                    }
                }
                doFormat(xsw, --indentLevel);
                xsw.writeEndElement();
            }
            xsw.writeEndDocument();
        }
        var headersXmlAsPath = Paths.get(headersXml.toURI());
        var headersXmlInSession = sessionFs.getPath("/headers.xml");
        Files.copy(headersXmlAsPath, headersXmlInSession, REPLACE_EXISTING);
    }

    /**
     * Writes an XML element of the form {@code &lt;tag&gt;text&lt;/tag&gt;} to the XMLStreamWriter.
     * 
     * @param xsw  XMLStreamWriter
     * @param tag  the tag name
     * @param text the text
     * @throws XMLStreamException if an exception occurs writing to the XMLStreamWriter.
     */
    private void writeXmlElement(XMLStreamWriter xsw, String tag, String text)
            throws XMLStreamException {
        xsw.writeStartElement(tag);
        xsw.writeCharacters(text);
        xsw.writeEndElement();
    }

    /**
     * Writes a line separator and character array (filled w/ spaces) to the XMLStreamWriter.
     * 
     * <P>
     * The number of spaces to indent is calculated as {@code INDENT * indentLevel} where
     * {@code INDENT} is set by the {@code jttp.indent} system property on the command line or the
     * default of {@code 2}.
     * 
     * @param xsw         the XMLStreamWriter.
     * @param indentLevel indent level.
     * @throws XMLStreamException if an exception occurs during formatting.
     */
    private void doFormat(XMLStreamWriter xsw, int indentLevel) throws XMLStreamException {
        if (formatSessionXml) {
            xsw.writeCharacters(AccessController.doPrivileged(
                    (PrivilegedAction<String>) () -> System.getProperty("line.separator")));

            if (indentLevel > 0) {
                var spaces = new char[INDENT * indentLevel];
                Arrays.fill(spaces, ' ');
                xsw.writeCharacters(spaces, 0, spaces.length);
            }
        }
    }

    /**
     * @param sessionName name for the session zip file.
     * @param url         the URI for the Jttp request.
     * 
     * @return a URI for the zip file to use as the basis for a pseudo FileSystem object.
     */
    private URI initSessionFsUri(String sessionName, URI url) {
        var hostDir =
                url.getPort() != -1 ? format("%s_%d", url.getHost(), url.getPort()) : url.getHost();
        var saveDir = Paths.get(Util.getSessionsDirectory(), hostDir);
        if (!saveDir.toFile().exists()) {
            saveDir.toFile().mkdirs();
        }
        return URI.create(format("jar:file:%s/%s.zip", saveDir.toString(), sessionName));
    }
}
