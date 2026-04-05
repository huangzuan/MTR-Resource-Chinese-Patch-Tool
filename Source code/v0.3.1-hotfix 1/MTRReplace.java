/*
 MTR Resource Chinese Patch Tool

 功能：
 自动替换 MTR 模组资源文件，实现资源名称汉化

 支持版本：
 MTR 4.0.0.x

 作者：
 huangzuan

 GitHub:
 https://github.com/huangzuan/MTR-Resource-Chinese-Patch-Tool

 说明：
 本程序不会包含 MTR 模组本体。
 只会修改用户本地已经安装的 MTR 模组。
*/

import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.awt.*;

public class MTRReplace {

    // =====================================================
    // MTR内部特征class
    // 用于识别jar是否是MTR模组
    // =====================================================
    static String TARGET_CLASS =
            "org/mtr/libraries/okio/internal/-FileSystem$commonListRecursively$1.class";

    // =====================================================
    // MTR资源JSON路径
    // =====================================================
    static String TARGET_JSON =
            "assets/mtr/mtr_custom_resources.json";


    // =====================================================
    // Java程序入口
    // =====================================================
    public static void main(String[] args) throws Exception {

        // 修改计数
        int count = 0;


        // =================================================
        // 创建GUI窗口
        // =================================================
        JFrame frame = new JFrame("MTR Replace");

        // 底部提示
        JLabel label = new JLabel("");

         frame.setLayout(new BorderLayout());
         frame.add(label, BorderLayout.CENTER);

        frame.setSize(300, 80);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);


        // =================================================
        // 加载程序图标
        // =================================================
        ImageIcon icon =
                new ImageIcon(
                        MTRReplace.class.getResource("/duke.png")
                );


        // =================================================
        // 获取系统信息
        // =================================================
        String os = System.getProperty("os.name").toLowerCase();

        String home = System.getProperty("user.home");

        String mcRoot;


        // =================================================
        // 判断 .minecraft 位置
        // =================================================
        if (os.contains("win")) {

            mcRoot = System.getenv("APPDATA") + "\\.minecraft";

        } else if (os.contains("mac")) {

            mcRoot = home + "/Library/Application Support/minecraft";

        } else {

            mcRoot = home + "/.minecraft";

        }


        // =================================================
        // 官方启动器 versions
        // =================================================
        File versions = new File(mcRoot + "/versions");


        // GUI列表模型
        DefaultListModel<String> model = new DefaultListModel<>();

        // 实例名称 -> mods目录
        Map<String, File> instanceMap = new HashMap<>();


        // =================================================
        // 官方启动器 mods
        // =================================================
        File officialMods = new File(mcRoot + "/mods");

        if (officialMods.exists()) {

            model.addElement("官方启动器 mods");

            instanceMap.put("官方启动器 mods", officialMods);

        }


        // =================================================
        // 扫描 versions 实例
        // =================================================
        if (versions.exists()) {

            File[] instances = versions.listFiles(File::isDirectory);

            if (instances != null)

                for (File inst : instances) {

                    File mods = new File(inst, "mods");

                    if (!mods.exists()) continue;

                    File[] jars = mods.listFiles((d,n)->n.endsWith(".jar"));

                    if (jars == null) continue;

                    for (File jar : jars) {

                        if (isMTRJar(jar)) {

                            String name =
                                    "Minecraft - " + inst.getName();

                            model.addElement(name);

                            instanceMap.put(name, mods);

                            break;

                        }

                    }

                }

        }


        // =================================================
        // 手动选择mods
        // =================================================
        model.addElement("手动选择 mods 文件夹");


        if (model.isEmpty()) {

            JOptionPane.showMessageDialog(
                    null,
                    "没有找到包含 MTR 的实例"
            );

            return;

        }


        // =================================================
        // 创建实例选择界面
        // =================================================
        JList<String> list = new JList<>(model);

        int result =
                JOptionPane.showConfirmDialog(

                        null,
                        new JScrollPane(list),
                        "选择实例",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE,
                        icon

                );

        if (result != JOptionPane.OK_OPTION) return;


        String selected = list.getSelectedValue();

        File mods;


        // =================================================
        // 手动选择
        // =================================================
        if (selected.equals("手动选择 mods 文件夹")) {

            JFileChooser chooser = new JFileChooser();

            chooser.setFileSelectionMode(
                    JFileChooser.DIRECTORIES_ONLY
            );

            int r = chooser.showOpenDialog(null);

            if (r != JFileChooser.APPROVE_OPTION)
                return;

            mods = chooser.getSelectedFile();

        } else {

            mods = instanceMap.get(selected);

        }


        if (mods == null || !mods.exists()) {

            JOptionPane.showMessageDialog(null,"mods 文件夹不存在");

            return;

        }


        // =================================================
        // 获取mods目录所有jar
        // =================================================
        File[] jars = mods.listFiles((d,n)->n.endsWith(".jar"));

        if (jars == null) return;


        // =================================================
        // 替换MTR资源
        // =================================================
        for (File jar : jars) {

            if (isMTRJar(jar)) {

                label.setText("正在替换或加载中，请勿关闭程序");

                // 备份
                backupJar(jar);

                // 替换JSON
                replaceJson(jar);

                count++;

            }

        }


        // =================================================
        // 完成提示
        // =================================================
        JOptionPane.showMessageDialog(
                null,
                "完成，修改了 " + count + " 个 MTR jar"
        );

        frame.dispose();

    }


    // =====================================================
    // 判断jar是否是MTR主模组
    // =====================================================
    static boolean isMTRJar(File jar) {

        if (!jar.getName().toLowerCase().contains("mtr"))
            return false;

        try (JarFile jf = new JarFile(jar)) {

            ZipEntry entry = jf.getEntry("fabric.mod.json");

            if (entry == null) return false;

            InputStream in = jf.getInputStream(entry);

            String json = new String(in.readAllBytes());

            if (!json.contains("\"id\": \"mtr\""))
                return false;

            if (jf.getEntry("assets/mtr") == null)
                return false;

            return true;

        } catch (Exception e) {

            return false;

        }

    }


    // =====================================================
    // 备份原始jar
    // =====================================================
    static void backupJar(File jar) throws Exception {

        File backup =
                new File(jar.getParent(),
                        jar.getName() + ".jar.backup");

        if (backup.exists()) return;

        Files.copy(jar.toPath(), backup.toPath());

    }


    // =====================================================
    // 替换MTR资源JSON
    // =====================================================
    static void replaceJson(File jar) throws Exception {

        File temp =
                new File(jar.getParent(), "temp.jar");

        ZipInputStream zin =
                new ZipInputStream(new FileInputStream(jar));

        ZipOutputStream zout =
                new ZipOutputStream(new FileOutputStream(temp));

        ZipEntry entry;

        while ((entry = zin.getNextEntry()) != null) {

            String name = entry.getName();

            if (name.equals("assets/mtr/mtr_custom_resources.json")
                    || name.equals("assets/mtr/lang/en_us.json")
                    || name.equals("assets/mtr/lang/zh_cn.json")) {

                zout.putNextEntry(new ZipEntry(name));

                String fileName =
                        name.substring(name.lastIndexOf("/") + 1);

                InputStream replaceStream =
                        MTRReplace.class.getResourceAsStream("/" + fileName);

                if (replaceStream != null)
                    replaceStream.transferTo(zout);

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