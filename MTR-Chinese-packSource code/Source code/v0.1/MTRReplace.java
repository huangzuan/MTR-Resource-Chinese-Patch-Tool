import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;

public class MTRReplace {

    static String TARGET_CLASS = "org/mtr/libraries/okio/internal/-FileSystem$commonListRecursively$1.class";
    static String TARGET_JSON = "assets/mtr/mtr_custom_resources.json";

    public static void main(String[] args) throws Exception {

        String mcPath = System.getProperty("user.home") + "/Library/Application Support/minecraft/versions";

        File versions = new File(mcPath);

        if (!versions.exists()) {
            JOptionPane.showMessageDialog(null, "找不到 Minecraft versions 文件夹");
            return;
        }

        File[] instances = versions.listFiles(File::isDirectory);

        DefaultListModel<String> model = new DefaultListModel<>();

        for (File instance : instances) {

            File mods = new File(instance, "mods");
            if (!mods.exists()) continue;

            File[] jars = mods.listFiles((d, name) -> name.endsWith(".jar"));
            if (jars == null) continue;

            for (File jar : jars) {

                if (isMTRJar(jar)) {
                    model.addElement(instance.getName());
                    break;
                }

            }
        }

        if (model.isEmpty()) {
            JOptionPane.showMessageDialog(null, "没有找到包含 MTR 的实例");
            return;
        }

        JList<String> list = new JList<>(model);

        int result = JOptionPane.showConfirmDialog(
                null,
                new JScrollPane(list),
                "选择实例",
                JOptionPane.OK_CANCEL_OPTION
        );

        if (result != JOptionPane.OK_OPTION) return;

        String selected = list.getSelectedValue();

        File mods = new File(mcPath + "/" + selected + "/mods");

        File[] jars = mods.listFiles((d, name) -> name.endsWith(".jar"));

        for (File jar : jars) {

            if (isMTRJar(jar)) {

                replaceJson(jar);

                JOptionPane.showMessageDialog(null, "替换完成:\n" + jar.getName());

            }
        }
    }

    static boolean isMTRJar(File jar) {

        try (JarFile jf = new JarFile(jar)) {

            ZipEntry classEntry = jf.getEntry(TARGET_CLASS);
            ZipEntry jsonEntry = jf.getEntry(TARGET_JSON);

            return classEntry != null && jsonEntry != null;

        } catch (Exception e) {
            return false;
        }
    }

    static void replaceJson(File jar) throws Exception {

        File temp = new File(jar.getParent(), "temp.jar");

        ZipInputStream zin = new ZipInputStream(new FileInputStream(jar));
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(temp));

        ZipEntry entry;

        while ((entry = zin.getNextEntry()) != null) {

            String name = entry.getName();

            if (name.equals("assets/mtr/mtr_custom_resources.json") ||
                name.equals("assets/mtr/lang/en_us.json") ||
                name.equals("assets/mtr/lang/zh_cn.json")) {

                zout.putNextEntry(new ZipEntry(name));

                File replace = new File(name.substring(name.lastIndexOf("/") + 1));

                if (replace.exists()) {

                    Files.copy(replace.toPath(), zout);

                }

            } else {

                zout.putNextEntry(new ZipEntry(name));

                zin.transferTo(zout);

            }
        }

        zin.close();
        zout.close();

        Files.delete(jar.toPath());
        Files.move(temp.toPath(), jar.toPath());
    }
}