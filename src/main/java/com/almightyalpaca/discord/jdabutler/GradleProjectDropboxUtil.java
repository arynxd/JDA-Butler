package com.almightyalpaca.discord.jdabutler;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.sharing.ListSharedLinksResult;
import com.dropbox.core.v2.sharing.SharedLinkMetadata;
import com.kantenkugel.discordbot.versioncheck.VersionCheckerRegistry;
import com.kantenkugel.discordbot.versioncheck.items.VersionedItem;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class GradleProjectDropboxUtil
{
    public static String dropboxShareLink = null;

    public static final String DROPBOX_FILE_NAME = "/JDA/jda gradle setup example.zip";

    public static final String EXMAPLE_IMPL_URL = "https://raw.githubusercontent.com/DV8FromTheWorld/JDA/master/src/examples/java/MessageListenerExample.java";
    public static final File GRADLE_PROJECT_DIR = new File("gradle project/");
    public static final File GRADLE_BUILD_FILE = new File(GRADLE_PROJECT_DIR, "build.gradle");
    public static final File GRADLE_PROJECT_ZIP = new File("example gradle project for jda.zip");

    public static final File GRADLE_SETTINGS_FILE = new File(GRADLE_PROJECT_DIR, "settings.gradle");
    public static final File GRADLE_TEMP_DIR = new File(GRADLE_PROJECT_DIR, ".gradle/");

    public static final File SRC_MAIN_JAVA = new File(GRADLE_PROJECT_DIR, "src/main/java/");

    public static final File EXAMPLE_IMPL = new File(SRC_MAIN_JAVA, "MessageListenerExample.java");

    public static final File SRC_MAIN_RESOURCES = new File(GRADLE_PROJECT_DIR, "src/main/resources/");

    private static DbxClientV2 client = null;

    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    public static void createZip()
    {
        Bot.LOG.info("Creating gradle example zip...");
        try
        {
            if (GRADLE_PROJECT_DIR.exists())
                FileUtils.cleanDirectory(GRADLE_PROJECT_DIR);
            else
                GRADLE_PROJECT_DIR.mkdirs();

            if (GRADLE_PROJECT_ZIP.exists())
                GRADLE_PROJECT_ZIP.delete();

            final ProcessBuilder builder = new ProcessBuilder(GradleDownloader.getExecutableGradleFile().getAbsolutePath(), "--no-daemon", "init");
            builder.directory(GRADLE_PROJECT_DIR);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);

            final Process process = builder.start();

            process.waitFor(1L, TimeUnit.MINUTES);

            List<VersionedItem> jdaSingleton = Collections.singletonList(VersionCheckerRegistry.getItem("jda"));

            FileUtils.write(GRADLE_BUILD_FILE, GradleUtil.getBuildFile(GradleUtil.DEFAULT_PLUGINS, "MessageListenerExample", "1.0", "1.8", jdaSingleton, false), Charset.forName("UTF-8"));

            FileUtils.write(GRADLE_SETTINGS_FILE, "rootProject.name = 'Example gradle project for JDA'", Charset.forName("UTF-8"));

            FileUtils.deleteDirectory(GRADLE_TEMP_DIR);

            SRC_MAIN_JAVA.mkdirs();
            SRC_MAIN_RESOURCES.mkdirs();

            FileUtils.copyURLToFile(new URL(EXMAPLE_IMPL_URL), EXAMPLE_IMPL);

            final ZipFile zip = new ZipFile(GRADLE_PROJECT_ZIP);

            final ZipParameters parameters = new ZipParameters();
            parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_ULTRA);
            parameters.setDefaultFolderPath("exmaple gradle project for jda/");

            zip.addFolder(GRADLE_PROJECT_DIR, parameters);

            Bot.LOG.info("Zip creation finished!");

        }
        catch (final IOException | InterruptedException | ZipException e)
        {
            Bot.LOG.error("Error creating gradle example zip", e);
        }
    }

    public static void uploadProject()
    {
        createZip();
        init();

        if (client == null)
        {
            Bot.LOG.info("Skipping upload!");
            return;
        }

        Bot.LOG.info("Uploading gradle example zip...");

        try (InputStream in = new FileInputStream(GRADLE_PROJECT_ZIP))
        {

            client.files().uploadBuilder(DROPBOX_FILE_NAME).withMute(true).withMode(WriteMode.OVERWRITE).uploadAndFinish(in);
            fetchUrl();
            Bot.LOG.info("Zip uploading finished!");

        }
        catch (DbxException | IOException e)
        {
            Bot.LOG.error("Error uploading gradle project to dropbox", e);
        }

    }

    public static void fetchUrl()
    {
        init();
        if (client == null)
            return;
        try
        {
            ListSharedLinksResult start = client.sharing().listSharedLinksBuilder()
                    .withPath(DROPBOX_FILE_NAME)
                    .withDirectOnly(true)
                    .start();
            List<SharedLinkMetadata> links = start.getLinks();
            if(links.size() == 1)
            {
                String url = links.get(0).getUrl();
                dropboxShareLink = url.substring(0, url.length() - 1) + '1';
            }
            else
                dropboxShareLink = null;
        }
        catch(DbxException e)
        {
            Bot.LOG.warn("Could not retrieve gradle project metadata from dropbox", e);
        }
    }

    private static void init()
    {
        if (!initialized.getAndSet(true))
        {
            final String ACCESS_TOKEN = Bot.config.getString("dropbox.access_token", "");
            if(ACCESS_TOKEN.isEmpty())
                return;

            final DbxRequestConfig config = DbxRequestConfig.newBuilder("JDA-Butler").build();

            client = new DbxClientV2(config, ACCESS_TOKEN);
        }
    }
}