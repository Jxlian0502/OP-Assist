package dev.opsuchtprices;

import dev.opsuchtprices.update.UpdateChecker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    @Test
    void versionenVergleichen() {
        assertTrue(UpdateChecker.compare("1.16.1", "1.16.0") > 0);
        assertTrue(UpdateChecker.compare("v1.17.0", "1.16.9") > 0);
        assertTrue(UpdateChecker.compare("1.10.0", "1.9.0") > 0);
        assertEquals(0, UpdateChecker.compare("v1.16.0", "1.16.0"));
        assertTrue(UpdateChecker.compare("1.16", "1.16.1") < 0);
    }

    @Test
    void releaseLesen() {
        String json = """
                {"tag_name":"v1.17.0","draft":false,"prerelease":false,"assets":[
                  {"name":"op-assistent-1.17.0-sources.jar","browser_download_url":"https://x/s.jar"},
                  {"name":"op-assistent-1.17.0.jar","browser_download_url":"https://x/m.jar","digest":"sha256:abc"}]}""";
        var r = UpdateChecker.parseRelease(json);
        assertEquals("1.17.0", r.version());
        assertEquals("op-assistent-1.17.0.jar", r.jarName());
        assertEquals("https://x/m.jar", r.downloadUrl());
        assertEquals("abc", r.sha256());
        assertNull(UpdateChecker.parseRelease("{\"tag_name\":\"v2\",\"prerelease\":true,\"assets\":[]}"));
    }

    static java.nio.file.Path fakeJar(java.nio.file.Path dir, String name, String id, String version) throws Exception {
        java.nio.file.Path p = dir.resolve(name);
        try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(p))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("fabric.mod.json"));
            zip.write(("{\"id\":\"" + id + "\",\"version\":\"" + version + "\"}").getBytes());
            zip.closeEntry();
        }
        return p;
    }

    @Test
    void downloadPruefenUndTauschen(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var ok = fakeJar(dir, "ok.part", "opsuchtprices", "1.17.0");
        UpdateChecker.verify(ok, new UpdateChecker.Release("1.17.0", "op-assistent-1.17.0.jar", "u", null));
        var wrong = fakeJar(dir, "wrong.part", "andere-mod", "1.17.0");
        assertThrows(SecurityException.class, () -> UpdateChecker.verify(wrong,
                new UpdateChecker.Release("1.17.0", "x.jar", "u", null)));
        assertFalse(java.nio.file.Files.exists(wrong), "falsche Datei wird gelöscht");
        var badSum = fakeJar(dir, "sum.part", "opsuchtprices", "1.17.0");
        assertThrows(SecurityException.class, () -> UpdateChecker.verify(badSum,
                new UpdateChecker.Release("1.17.0", "x.jar", "u", "00ff")));

        // Tausch: alte .jar weg, Download wird zur neuen .jar
        var oldJar = fakeJar(dir, "opsuchtprices-1.16.0.jar", "opsuchtprices", "1.16.0");
        var download = fakeJar(dir, "opsuchtprices-1.17.0.jar.download", "opsuchtprices", "1.17.0");
        var target = dir.resolve("op-assistent-1.17.0.jar");
        dev.opsuchtprices.update.Swapper.main(new String[]{oldJar.toString(), download.toString(), target.toString(),
                dir.resolve("update.log").toString()});
        assertFalse(java.nio.file.Files.exists(oldJar));
        assertFalse(java.nio.file.Files.exists(download));
        assertTrue(java.nio.file.Files.exists(target));
        assertTrue(java.nio.file.Files.readString(dir.resolve("update.log")).contains("Update installiert"));
    }
}
