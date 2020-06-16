package com.github.argherna.jttp;

import static java.lang.String.format;

import java.io.StringWriter;
import java.util.prefs.Preferences;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;

final class Util {
    private Util() {
    }

    static String formatMarkup(Source source)
            throws TransformerFactoryConfigurationError, TransformerException {

        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xalan}indent-amount",
                Integer.toString(Jttp.INDENT));
        var sw = new StringWriter();
        transformer.transform(source, new StreamResult(sw));

        // Note: for some reason, the transformer inserts spaces and newlines in extant xml, so
        // get rid of all that extra by replacing the trailing spaces and newline with a simple
        // newline.
        return sw.toString().replaceAll("\n *\n", "\n").replace("?><", "?>\n<");
    }

    /**
     * @return the root save directory.
     */
    static String getBaseSaveDirectory() {
        return Preferences.userNodeForPackage(Jttp.class).node("directories").get("base",
                Jttp.LOCAL_SAVE_DIRECTORY);
    }

    /**
     * @return the name of the directory to save session data in.
     */
    static String getSessionsDirectory() {
        return format("%s%s%s", getBaseSaveDirectory(), Jttp.FILE_SEP, Preferences
                .userNodeForPackage(Jttp.class).node("directories").get("sessions", "sessions"));
    }

    /**
     * @return the name of the base directory where scripts are located.
     */
    static String getScriptsDirectory() {
        return format("%s%s%s", getBaseSaveDirectory(), Jttp.FILE_SEP,
                Preferences.userNodeForPackage(Jttp.class).node("directories").get("scripts", "scripts"));
    }

    /**
     * @return absolute path to the preferred downloads directory.
     */
    static String getDownloadsDirectory() {
        return Preferences.userNodeForPackage(Jttp.class).node("directories").get("downloads",
                format("%s%s%s", getBaseSaveDirectory(), Jttp.FILE_SEP, "downloads"));
    }
}
