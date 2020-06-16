package com.github.argherna.jttp;

import static java.lang.String.format;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;
import static java.net.URLEncoder.encode;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "jttp", mixinStandardHelpOptions = true, version = "1.0",
        resourceBundle = "com.github.argherna.jttp.messages_jttp", separator = " ",
        showAtFileInUsageHelp = true)
public class Jttp implements Runnable {

    private static final Integer BUF_SZ = 0x1000;

    private static final Integer CHUNK_SZ = 0x10000;

    static final ResourceBundle RB =
            ResourceBundle.getBundle("com.github.argherna.jttp.messages_jttp");

    private static final String BOUNDARY = Long.toHexString(System.currentTimeMillis());

    private static final String CRLF = "\r\n";

    static final String FILE_SEP = AccessController
            .doPrivileged((PrivilegedAction<String>) () -> System.getProperty("file.separator"));

    static final String LOCAL_SAVE_DIRECTORY = format("%s%s.%s",
            AccessController
                    .doPrivileged((PrivilegedAction<String>) () -> System.getProperty("user.home")),
            FILE_SEP, Jttp.class.getSimpleName().toLowerCase());

    static final String SYS_PROP_INDENT = "jttp.indent";

    private static final Integer DEFAULT_INDENT = 2;

    static final Integer INDENT = AccessController.doPrivileged(
            (PrivilegedAction<Integer>) () -> Integer.getInteger(SYS_PROP_INDENT, DEFAULT_INDENT));

    static final String SYS_PROP_KEEP_TEMP_FILES = "jttp.keep.tempfiles";

    static final System.Logger LOGGER = System.getLogger(Jttp.class.getName(), RB);

    private final PrintStream ps;

    private File tempResponse;

    private HttpURLConnection conn;

    private InputStream inStream;

    private Map<String, String> requestDataMap;

    private Map<String, List<String>> requestHeaders;

    private Map<String, File> uploadFiles;

    @Option(names = {"-A", "--auth"}, paramLabel = "user[:password]",
            descriptionKey = "jttp.opt.auth")
    private String auth;

    @Option(names = {"-d", "--download"}, descriptionKey = "jttp.opt.download")
    private boolean download;

    @Option(names = {"-M", "--request-mime-type"}, paramLabel = "mimetype",
            descriptionKey = "jttp.opt.reqmimetype")
    private RequestMimeType requestMimeType = RequestMimeType.JSON;

    private Session session;

    @Option(names = {"-N", "--no-verify"}, descriptionKey = "jttp.opt.noverify")
    private boolean noVerify;

    @Option(names = {"-O", "--offline"}, descriptionKey = "jttp.opt.offline")
    private boolean offline;

    @Option(names = {"-o", "--outfile"}, paramLabel = "filename",
            descriptionKey = "jttp.opt.output")
    private File outfile;

    @Option(names = {"-P", "--pretty-print"}, paramLabel = "NONE|COLOR|INDENT|ALL",
            descriptionKey = "jttp.opt.prettyprint")
    private PrettyPrint prettyPrint = PrettyPrint.ALL;

    @Option(names = "--pre-process-script-name", paramLabel = "scriptname",
            descriptionKey = "jttp.opt.preprocessscript")
    private String preProcessScriptName;

    @Option(names = "--pre-process-script-arg", paramLabel = "arg",
            descriptionKey = "jttp.opt.preprocessscriptarg")
    private String[] preProcessScriptArgs;

    @Option(names = "--post-process-script-name", paramLabel = "scriptname",
            descriptionKey = "jttp.opt.postprocessscript")
    private String postProcessScriptName;

    @Option(names = "--post-process-script-arg", paramLabel = "arg",
            descriptionKey = "jttp.opt.postprocessscriptarg")
    private String[] postProcessScriptArgs;

    @Option(names = {"-p", "--print"}, paramLabel = "entity", descriptionKey = "jttp.opt.print")
    private String print = "hb";

    @Option(names = {"-R", "--read-only-session"}, descriptionKey = "jttp.opt.readonlysession")
    private boolean readOnlySession;

    @Option(names = {"-S", "--session"}, paramLabel = "sessionname",
            descriptionKey = "jttp.opt.sessionname")
    private String sessionName;

    private String requestData = "";

    @Option(names = {"-v", "--verbose"}, descriptionKey = "jttp.opt.verbose")
    private boolean verbose;

    @Option(names = {"-X", "--request-method"}, paramLabel = "methodname",
            descriptionKey = "jttp.opt.method")
    private RequestMethod method = RequestMethod.GET;

    @Parameters(index = "0", descriptionKey = "jttp.arg.url", paramLabel = "url")
    private String urlString;

    @Parameters(index = "1..*", descriptionKey = "jttp.arg.reqitem", paramLabel = "request_item")
    private String[] requestItems;

    private URI url;

    /**
     * The main.
     * 
     * @param args command line arguments.
     */
    public static void main(String... args) {

        var saveDir = new File(Util.getBaseSaveDirectory());
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        int exitCode = new CommandLine(new Jttp()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Constructs a new instance of Jttp.
     */
    Jttp() {
        ps = System.out;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * <strong>Implementation note:</strong> this method is the instance entry point for Jttp. It is
     * executed in 5 phases:
     * <ol>
     * <li>{@link #setup() setup} initializes the state, then initializes the HttpURLConnection by
     * utilizing the state. If a {@code session name} is set on the command line, that session is
     * loaded and the headers and cookies contained in it become part of the request.
     * <li>{@link #preProcess() preProcess} executes the {@code pre-process-script} if set on the
     * command line. If script execution fails, the logger will write the reasons for the failure to
     * the log file and this method will continue to execute.
     * <li>{@link #process() process} executes the Http request. Response bodies are written as
     * temporary files.
     * <li>{@link #postProcess() postProcess} executes the {@code post-process-script} if set on the
     * command line. If script execution fails, the logger will write the reasons for the failure to
     * the log file and this method will continue to execute.
     * <li>{@link #finish() finish} determines how to handle the response (print it, save it to a
     * different location on disk, etc.).
     * </ol>
     * 
     * @throws RuntimeException if an uncaught exception or RuntimeException is thrown internally.
     */
    @Override
    public void run() {
        try {

            setup();
            preProcess();
            process();
            postProcess();
            finish();

        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Set up the components needed for the run.
     */
    void setup() throws IOException, URISyntaxException {
        initializeState();
        conn = (HttpURLConnection) url.toURL().openConnection();
        if (nonNull(sessionName) && !sessionName.isEmpty()) {
            session = new Session(sessionName, conn);
            try {
                session.load();
            } catch (Exception e) {
                LOGGER.log(WARNING, "logger.warning.xmlerror", e.getMessage());
                e.printStackTrace();
            }
        }

        setRequestHeaders();
        setRequestMethod();
        if (method.hasPayload()) {
            var mimeType = requestMimeType;
            if (!requestHeaders.containsKey("Content-Type")) {
                var contentType = mimeType == RequestMimeType.MULTIPART
                        ? format("%s; boundary=%s", mimeType.getContentType(), BOUNDARY)
                        : mimeType.getContentType();
                conn.setRequestProperty("Content-Type", contentType);
            }
            if (uploadFiles.isEmpty() && (!requestDataMap.isEmpty() || nonNull(inStream))) {
                requestData = nonNull(inStream) ? readFromInStream() : renderPayload();
            }
        }
    }

    /**
     * Executes the preprocessing script after {@link Jttp#setup() setup} executes and before the
     * {@link Jttp#process() process} method executes.
     */
    void preProcess() {
        if (nonNull(preProcessScriptName) && !preProcessScriptName.isEmpty()) {
            LOGGER.log(DEBUG, RB, "logger.debug.exec.preprocess");
            var jttpScriptObject = new JttpScriptObject(conn);
            executeScript(preProcessScriptName, jttpScriptObject, preProcessScriptArgs);
        }
    }

    /**
     * Sends request data and get the response.
     * 
     * <p>
     * <strong>Implementation Note:</strong> all response bodies are stored in a temporary file that
     * is deleted after the run.
     */
    void process() throws IOException {
        requireNonNull(conn, RB.getString("error.null.connection"));
        if (!offline() && conn.getDoOutput()
                && ((nonNull(requestData) && !requestData.isEmpty()) || !uploadFiles.isEmpty())) {
            try (var out = conn.getOutputStream()) {
                if (nonNull(requestData) && !requestData.isEmpty()) {
                    sendRequestData(out);
                } else {
                    sendMultipartData(out);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        InputStream responseStream = null;
        try {
            responseStream = offline() ? null : getInputStream();
        } catch (IOException e) {
            responseStream = conn.getErrorStream();
        } finally {
            if (nonNull(responseStream)) {
                tempResponse = File.createTempFile(
                        format("%s-response", getClass().getSimpleName().toLowerCase()),
                        ".download");
                boolean deleteTempFiles =
                        !AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> Boolean
                                .getBoolean(SYS_PROP_KEEP_TEMP_FILES));
                if (deleteTempFiles) {
                    tempResponse.deleteOnExit();
                }
                var xfered = responseStream.transferTo(new FileOutputStream(tempResponse));
                LOGGER.log(TRACE, "logger.trace.bytes.transferred", xfered,
                        tempResponse.toString());
            }
            conn.disconnect();
        }
    }

    /**
     * Executes the postprocessing script after the {@link Jttp#process() process} method executes
     * and before the {@link Jttp#finish() finish} method executes.
     */
    void postProcess() {
        if (nonNull(postProcessScriptName) && !postProcessScriptName.isEmpty()) {
            LOGGER.log(DEBUG, "logger.debug.exec.postprocess");
            var jttpScriptObject = new JttpScriptObject(conn, tempResponse);
            executeScript(postProcessScriptName, jttpScriptObject, postProcessScriptArgs);
        }
    }

    /**
     * Handles output.
     */
    void finish() throws IOException, URISyntaxException {

        var headerRenderer = new HeaderRenderer(conn, colorOutput());

        if (printRequestHeaders()) {
            headerRenderer.requestLine(ps);
            headerRenderer.requestHeaders(ps);
        }

        if (printRequestBody()) {
            createContentRenderer(conn.getRequestProperty("Content-Type"),
                    requestData.toCharArray()).run();
            ps.println();
            ps.println();
        }

        if (!offline()) {

            if (!readOnlySession() && nonNull(session)) {
                try {
                    session.save(requestData, tempResponse);
                } catch (XMLStreamException | TransformerFactoryConfigurationError
                        | TransformerException e) {
                    LOGGER.log(WARNING, RB, "logger.warning.xmlerror", e.getMessage());
                    e.printStackTrace();
                }
            }

            if (printResponseHeaders()) {
                headerRenderer.responseStatusLine(ps);
                headerRenderer.responseHeaders(ps);
                ps.println();
            }

            if (nonNull(tempResponse)) {
                if (isDownload()) {
                    doDownload();
                } else if (printResponseBody()) {
                    createContentRenderer(conn.getContentType(), tempFileToChars()).run();
                    ps.println();
                }
            } else {
                LOGGER.log(INFO, "logger.info.no.response.body.sent");
            }
        }
    }

    /**
     * Prior to setup but after construction, make sure base objects needed for the run are
     * instantiated.
     */
    private void initializeState() {
        initializeUri();
        initializeRequestHeaders();
        initializeAuthenticator();
        initializeNoVerify();
        initializeRequestData();
    }

    /**
     * Initializes the request Uri with query parameters if set from the command line.
     */
    private void initializeUri() {
        var uri = new StringBuilder();

        if (urlString.startsWith(":") || urlString.startsWith("/")) {
            uri.append("http://localhost");
        }
        uri.append(urlString);
        if (nonNull(requestItems)) {
            var qparams = Arrays.stream(requestItems).filter(i -> i.contains("=="))
                    .map(i -> i.replace("==", "=")).collect(joining("&"));

            if (!qparams.isEmpty()) {
                uri.append("?").append(qparams);
            }
        }

        url = URI.create(uri.toString());
    }

    /**
     * Initializes the request headers if set from the command line.
     * 
     * <p>
     * If none set, request headers are an empty Map to avoid NPEs.
     */
    private void initializeRequestHeaders() {
        requestHeaders =
                nonNull(requestItems) ? Arrays.stream(requestItems).filter(i -> i.contains(":"))
                        .map(i -> new Pair<String, String>(i.split(":")[0], i.split(":")[1]))
                        .collect(groupingBy(Pair::getN, mapping(Pair::getV, toList()))) : Map.of();
    }

    /**
     * Initializes an Authenticator object if auth credentials were set on the command line.
     */
    private void initializeAuthenticator() {
        if (nonNull(auth) && !auth.isEmpty() && !auth.isBlank()) {
            Authenticator a;
            if (auth.indexOf(":") != -1) {
                var creds = auth.split(":");
                a = new JttpAuthenticator(creds[0], creds[1].toCharArray());
            } else {
                a = new JttpAuthenticator(auth);
            }
            Authenticator.setDefault(a);
        }
    }

    /**
     * If {@code -N} is set on the command line, set up TrustManager & HostNameVerifier to trust
     * everything.
     * 
     * <p>
     * Emits a log message at warning that this option was selected.
     */
    private void initializeNoVerify() {
        if (noVerify) {
            TrustManager[] trustAllCertificates = new TrustManager[] {new X509TrustManager() {

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                }
            }};

            HostnameVerifier trustAllHostnames = new HostnameVerifier() {

                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            try {
                LOGGER.log(WARNING, "logger.warning.noverify", Jttp.class.getSimpleName());
                System.setProperty("jsse.enableSNIExtension", "false");
                var sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCertificates, new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Initializes request data and resets the MIME type if needed.
     */
    private void initializeRequestData() {
        requestDataMap =
                nonNull(requestItems)
                        ? Arrays.stream(requestItems)
                                .filter(i -> i.contains("=") && !i.contains("=="))
                                .map(i -> new Pair<String, String>(i.split("=")[0],
                                        i.split("=")[1]))
                                .collect(toMap(Pair::getN, Pair::getV))
                        : Map.of();
        uploadFiles = nonNull(requestItems)
                ? Arrays.stream(requestItems).filter(i -> i.contains("@"))
                        .map(i -> new Pair<String, File>(i.split("@")[0],
                                new File(i.split("@")[1])))
                        .collect(toMap(Pair::getN, Pair::getV))
                : Map.of();

        if (!requestDataMap.isEmpty() && method == RequestMethod.GET) {
            method = RequestMethod.POST;
        }
        if (!uploadFiles.isEmpty()) {
            requestMimeType = RequestMimeType.MULTIPART;
        }
        if (uploadFiles.isEmpty() && requestDataMap.isEmpty() && method.hasPayload()) {
            inStream = System.in;
        }
    }

    /**
     * Set the request headers on the HttpURLConnection.
     */
    private void setRequestHeaders() throws IOException, URISyntaxException {
        for (String headername : requestHeaders.keySet()) {
            var values = requestHeaders.get(headername);
            conn.setRequestProperty(headername, values.get(0));
            for (var value : values.subList(1, values.size())) {
                conn.addRequestProperty(headername, value);
            }
        }
        if (!requestHeaders.containsKey("Accept-Charset")) {
            conn.setRequestProperty("Accept-Charset", defaultCharset().name());
        }
        if (!requestHeaders.containsKey("Accept")) {
            conn.setRequestProperty("Accept", "*/*");
        }
        if (!requestHeaders.containsKey("User-Agent")) {
            conn.setRequestProperty("User-Agent", userAgent(this));
        }
        if (conn instanceof HttpsURLConnection) {
            conn.setRequestProperty("Accept-Encoding", "gzip");
        }
    }

    /**
     * Set the request method on the HttpURLConnection.
     * 
     * @throws IOException if an @link IOException occurs.
     */
    private void setRequestMethod() throws IOException {
        if (method == RequestMethod.PATCH) {
            // per https://stackoverflow.com/a/32503192/37776
            // The server MUST support this to work. Need to look at
            // https://stackoverflow.com/a/40606633/37776 as a more versatile (and invasive)
            // solution. However, in Java 9+, the sun.www.net.protocol.https.HttpsURLConnection
            // class is not exported from the java.base module and therefore creates a compilation
            // error.
            conn.setDoOutput(true);
            conn.setRequestProperty("X-HTTP-Method-Override", method.name());
            conn.setRequestMethod(RequestMethod.POST.name());
        } else {
            conn.setRequestMethod(method.name());
            if (method.hasPayload()) {
                conn.setDoOutput(true);
            }
        }
    }

    private String renderPayload() {
        if (requestMimeType == RequestMimeType.FORM) {
            return renderWwwFormUrlEncodedString();
        }
        return renderSimpleJson();
    }

    private String renderWwwFormUrlEncodedString() {
        return requestDataMap.keySet().stream()
                .map(k -> format("%s=%s", k, encode(requestDataMap.get(k), defaultCharset())))
                .collect(joining("&"));
    }

    private String renderSimpleJson() {
        return format("{%s}",
                requestDataMap.keySet().stream()
                        .map(k -> format("\"%s\":\"%s\"", k, requestDataMap.get(k)))
                        .collect(joining(", ")));
    }

    private String readFromInStream() {
        var sb = new StringBuilder();
        try (var sc = new Scanner(inStream);) {
            while (sc.hasNextLine())
                sb.append(sc.nextLine());
        }
        return sb.toString();
    }

    private byte[] tempFileToBytes() throws IOException {
        try (var fis = new FileInputStream(tempResponse)) {
            return toByteArray(fis);
        }
    }

    private char[] tempFileToChars() throws IOException {
        var fileBytes = tempFileToBytes();
        var chars = new char[fileBytes.length];
        for (int i = 0; i < fileBytes.length; i++) {
            chars[i] = (char) (fileBytes[i] & 0xff);
        }
        return chars;
    }

    private ContentRenderer createContentRenderer(String contentType, char[] content)
            throws IOException {
        ContentRenderer renderer = null;
        if ((!formatOutput() && !colorOutput()) || isNull(contentType) || contentType.isEmpty()) {
            renderer = ContentRenderer.newRawInstance(content, ps);
        } else if (contentType.contains("json")) {
            renderer = new JsonRenderer(content, ps, colorOutput(), formatOutput());
        } else if (contentType.contains("xml")) {
            renderer = new MarkupRenderer(content, ps, colorOutput(), formatOutput());
        } else if (contentType.contains("html")) {
            // Don't format Html.
            renderer = new MarkupRenderer(content, ps, colorOutput(), false);
        } else if (contentType.equals(RequestMimeType.FORM.getContentType())) {
            renderer = new FormDataRenderer(content, ps, colorOutput());
        } else {
            renderer = ContentRenderer.newRawInstance(content, ps);
        }
        return renderer;
    }

    /**
     * Sends encoded request data (read from command line either from arguments or from redirected
     * input) through the OutputStream.
     * 
     * @param output the OutputStream.
     * @throws IOException if an IOException occurs.
     */
    private void sendRequestData(OutputStream output) throws IOException {
        output.write(requestData.getBytes());
    }

    /**
     * Sends request data and files specified from the command line as a multipart form.
     * 
     * @param output the OutputStream.
     * @throws IOException if an IOException.
     */
    private void sendMultipartData(OutputStream output) throws IOException {
        conn.setChunkedStreamingMode(CHUNK_SZ);
        try (var pw = new PrintWriter(output, true, defaultCharset())) {
            var somethingWritten = false;
            for (var entry : requestDataMap.entrySet()) {
                pw.append("--").append(BOUNDARY).append(CRLF)
                        .append("Content-Disposition: form-data; name=\"").append(entry.getKey())
                        .append("\"").append(CRLF).append("Content-Type: text/plain; charset=")
                        .append(defaultCharset().name()).append(CRLF).append(CRLF)
                        .append(entry.getValue()).append(CRLF).flush();
                somethingWritten = true;
            }

            for (var entry : uploadFiles.entrySet()) {
                var contentType =
                        URLConnection.guessContentTypeFromName(entry.getValue().getName());
                pw.append("--").append(BOUNDARY).append(CRLF)
                        .append("Content-Disposition: form-data; name=\"").append(entry.getKey())
                        .append("\"").append(CRLF).append("Content-Type: ");
                if (isNull(contentType) || !contentType.contains("text")) {
                    contentType = "application/octet";
                    pw.append(contentType);
                    pw.append(CRLF).append("Content-Transfer-Encoding: binary").append(CRLF);
                } else {
                    pw.append(contentType).append("; charset=").append(defaultCharset().toString())
                            .append(CRLF);
                }
                pw.append(CRLF).flush();
                Files.copy(entry.getValue().toPath(), output);
                output.flush();
                pw.append(CRLF).flush();
                somethingWritten = true;
            }
            if (somethingWritten) {
                pw.append("--").append(BOUNDARY).append("--").append(CRLF).flush();
            }
        }
    }

    private InputStream getInputStream() throws IOException {
        // Assuming we already tested for offline run.
        if (conn instanceof HttpsURLConnection) {
            if (nonNull(conn.getHeaderField("Content-Encoding"))
                    && conn.getHeaderField("Content-Encoding").equals("gzip")) {
                return new GZIPInputStream(conn.getInputStream());
            } else {
                return conn.getInputStream();
            }
        } else {
            return conn.getInputStream();
        }
    }

    private void doDownload() throws IOException {
        Path download = getDownloadPath();
        if (!Files.exists(download.getParent())) {
            Files.createDirectories(download.getParent());
        }
        Files.move(tempResponse.toPath(), download, ATOMIC_MOVE);
    }

    private Path getDownloadPath() {
        var downloadDir = new File(Util.getDownloadsDirectory());
        Path download = null;
        if (nonNull(outfile)) {
            if (outfile.isAbsolute()) {
                download = outfile.toPath();
            } else {
                download = new File(downloadDir, outfile.toString()).toPath();
            }
        } else {
            var filename = conn.getURL().getFile();
            download = new File(downloadDir, filename).toPath();
        }
        return download;
    }

    private boolean offline() {
        return offline;
    }

    private boolean printRequestHeaders() {
        return verbose || print.contains("H");
    }

    private boolean printRequestBody() {
        return verbose || print.contains("B");
    }

    private boolean printResponseHeaders() {
        return verbose || print.contains("h");
    }

    private boolean printResponseBody() {
        return !download && (verbose || print.contains("b"));
    }

    private boolean readOnlySession() {
        return readOnlySession;
    }

    private boolean isDownload() {
        return download;
    }

    private boolean colorOutput() {
        return !download && (prettyPrint == PrettyPrint.ALL || prettyPrint == PrettyPrint.COLORS);
    }

    private boolean formatOutput() {
        return !download && (prettyPrint == PrettyPrint.ALL || prettyPrint == PrettyPrint.INDENT);
    }

    private void executeScript(String scriptname, JttpScriptObject jttpScriptObject,
            String[] scriptargs) {
        var scriptEngineName = scriptEngineNameFrom(scriptname);
        var manager = new ScriptEngineManager();
        var engine = manager.getEngineByName(scriptEngineName);
        if (isNull(engine)) {
            LOGGER.log(WARNING, "logger.warning.null.scriptengine", scriptEngineName);
            return;
        }
        engine.put("jttpScriptObject", jttpScriptObject);
        if (nonNull(scriptargs) && scriptargs.length > 0) {
            engine.put("args", scriptargs);
        } else {
            engine.put("args", new String[0]);
        }
        var scriptFilename = format("%s%s%s", Util.getScriptsDirectory(), FILE_SEP, scriptname);
        try (var scriptFileReader = new FileReader(scriptFilename)) {
            engine.eval(scriptFileReader);
        } catch (FileNotFoundException e) {
            LOGGER.log(WARNING, () -> MessageFormat
                    .format(RB.getString("logger.warning.script.file.not.found"), scriptFilename),
                    e);
            return;
        } catch (IOException e) {
            LOGGER.log(WARNING, () -> MessageFormat
                    .format(RB.getString("logger.warning.script.file.close"), scriptFilename), e);
            return;
        } catch (ScriptException e) {
            LOGGER.log(
                    WARNING, () -> MessageFormat
                            .format(RB.getString("logger.warning.script.exec.error"), scriptname),
                    e);
            return;
        }
    }

    private String scriptEngineNameFrom(String scriptname) {
        var scriptEngineName = "nashorn";
        if (scriptname.indexOf('.') != -1) {
            var namecomponents = scriptname.split("\\.");
            scriptEngineName = namecomponents[namecomponents.length - 1];
        }
        return scriptEngineName;
    }

    private static String userAgent(Jttp instance) {
        return versionString(instance, false);
    }

    private static String versionString(Object instance, boolean includePlatformData) {
        return format("%s/%d", Jttp.class.getSimpleName(), 1);
    }

    /**
     * @param in an InputStream.
     * @return a byte array with the contents of the InputStream.
     * @throws IOException if an IOException occurs.
     */
    private static byte[] toByteArray(InputStream in) throws IOException {
        var bos = new ByteArrayOutputStream();
        var buf = new byte[BUF_SZ];
        while (true) {
            var read = in.read(buf);
            if (read == -1) {
                break;
            }
            bos.write(buf, 0, read);
        }
        return bos.toByteArray();
    }
}
