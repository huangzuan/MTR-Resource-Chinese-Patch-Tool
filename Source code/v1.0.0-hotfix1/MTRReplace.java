/*
 MTR Resource Chinese Patch Tool

 功能：
 自动替换 MTR 模组资源文件，实现资源名称汉化虽然日语也可以用吧

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
    // 语言系统
    // =====================================================

    // 当前语言
    static String LANG;

    // 用户是否手动选择过语言
    static boolean USER_SELECTED_LANG = false;

    // 界面文本
    static String TEXT_TITLE;
    static String TEXT_SELECT_INSTANCE;
    static String TEXT_NO_INSTANCE;
    static String TEXT_MODS_NOT_EXIST;
    static String TEXT_WORKING;
    static String TEXT_DONE;
    static String TEXT_OFFICIAL;
    static String TEXT_MANUAL;


    // =====================================================
    // 初始化语言
    // 根据系统语言自动选择
    // =====================================================
    static void initLanguage() {

        // 如果用户手动选择过语言
        // 就不要再根据系统语言修改
        if (USER_SELECTED_LANG) {
            applyLanguage();
            return;
        }

        String systemLang = Locale.getDefault().getLanguage();

        if (systemLang.equals("zh")) {
            LANG = "zh";
        } else {
            LANG = "en";
        }

        applyLanguage();
    }


    // =====================================================
    // 应用语言
    // =====================================================
    static void applyLanguage() {

        if (LANG.equals("zh")) {

            TEXT_TITLE = "MTR Replace";
            TEXT_SELECT_INSTANCE = "选择实例";
            TEXT_NO_INSTANCE = "没有找到包含 MTR 的实例";
            TEXT_MODS_NOT_EXIST = "mods 文件夹不存在";
            TEXT_WORKING = "正在替换或加载中，请勿关闭程序";
            TEXT_DONE = "完成，修改了 ";
            TEXT_OFFICIAL = "官方启动器 mods";
            TEXT_MANUAL = "手动选择 mods 文件夹";

        } else {

            TEXT_TITLE = "MTR Replace";
            TEXT_SELECT_INSTANCE = "Select Instance";
            TEXT_NO_INSTANCE = "No MTR instance found";
            TEXT_MODS_NOT_EXIST = "mods folder not found";
            TEXT_WORKING = "Replacing files, please do not close";
            TEXT_DONE = "Finished. Modified ";
            TEXT_OFFICIAL = "Official Launcher mods";
            TEXT_MANUAL = "Select mods folder manually";

        }
    
    }

// =====================================================
// Java程序入口
// =====================================================
public static void main(String[] args) throws Exception {

    // ----------------------------
    // 初始化语言
    // ----------------------------
    initLanguage();

    int count = 0; // 记录被修改的 MTR jar 数量

    // ----------------------------
    // 创建主窗口
    // ----------------------------
    JFrame frame = new JFrame(TEXT_TITLE);
    JLabel label = new JLabel(""); // 显示进度信息
    frame.setLayout(new BorderLayout());
    frame.add(label, BorderLayout.CENTER);
    frame.setSize(300, 80);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);

    // ----------------------------
    // 加载程序图标
    // ----------------------------
    ImageIcon icon = new ImageIcon(MTRReplace.class.getResource("/duke.png"));

    // ----------------------------
    // 获取系统信息
    // ----------------------------
    String os = System.getProperty("os.name").toLowerCase();
    String home = System.getProperty("user.home");
    String mcRoot;

    // ----------------------------
    // 判断 .minecraft 位置
    // ----------------------------
    if (os.contains("win")) {
        mcRoot = System.getenv("APPDATA") + "\\.minecraft";
    } else if (os.contains("mac")) {
        mcRoot = home + "/Library/Application Support/minecraft";
    } else {
        mcRoot = home + "/.minecraft";
    }

    // ----------------------------
    // 官方启动器 versions
    // ----------------------------
    File versions = new File(mcRoot + "/versions");
    DefaultListModel<String> model = new DefaultListModel<>();
    Map<String, File> instanceMap = new HashMap<>();

    // ----------------------------
    // 官方启动器 mods
    // ----------------------------
    File officialMods = new File(mcRoot + "/mods");
    if (officialMods.exists()) {
        model.addElement(TEXT_OFFICIAL);
        instanceMap.put(TEXT_OFFICIAL, officialMods);
    }

    // ----------------------------
    // 扫描 versions 目录
    // ----------------------------
    if (versions.exists()) {
        File[] instances = versions.listFiles(File::isDirectory);
        if (instances != null) {
            for (File inst : instances) {
                File mods = new File(inst, "mods");
                if (!mods.exists()) continue;
                File[] jars = mods.listFiles((d, n) -> n.endsWith(".jar"));
                if (jars == null) continue;
                for (File jar : jars) {
                    if (isMTRJar(jar)) {
                        String name = "Minecraft - " + inst.getName();
                        model.addElement(name);
                        instanceMap.put(name, mods);
                        break;
                    }
                }
            }
        }
    }

    // ----------------------------
    // 添加手动选择 mods
    // ----------------------------
    model.addElement(TEXT_MANUAL);

    if (model.isEmpty()) {
        JOptionPane.showMessageDialog(null, TEXT_NO_INSTANCE);
        return;
    }

    // ----------------------------
    // 创建 GUI 组件
    // ----------------------------
    JList<String> list = new JList<>(model); // 实例列表
    JButton langButton = new JButton(LANG.equals("zh") ? "🌐 语言" : "🌐 Language"); // 语言切换按钮
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JScrollPane(list), BorderLayout.CENTER);
    panel.add(langButton, BorderLayout.SOUTH);

    // ----------------------------
    // 语言切换按钮事件（刷新界面，不关闭窗口）
    // ----------------------------
    langButton.addActionListener(e -> {
    String[] options = {"中文", "English"};
    String selected = (String) JOptionPane.showInputDialog(
            panel,
            "选择语言 / Select Language",
            "Language",
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            options[0]
    );
    if (selected == null) return;

    // 设置新语言
    LANG = selected.equals("中文") ? "zh" : "en";
    USER_SELECTED_LANG = true;
    applyLanguage(); // 更新 TEXT_* 变量

    // 更新列表里的文本
    for (int i = 0; i < model.size(); i++) {
        String item = model.get(i);
        // 官方 mods
        if (item.equals(TEXT_OFFICIAL) || item.equals("Official Launcher mods") || item.equals("官方启动器 mods")) {
            model.set(i, TEXT_OFFICIAL);
        }
        // 手动选择 mods
        else if (item.equals(TEXT_MANUAL) || item.equals("Select mods folder manually") || item.equals("手动选择 mods")) {
            model.set(i, TEXT_MANUAL);
        }
        // Minecraft 实例名称，这里保持原来的实例名，不改语言
        // 如果你想改成 "Minecraft - 实例名" 的英文/中文，可以在扫描时保存原始名和显示名映射
    }
});

    // ----------------------------
    // 弹出选择对话框
    // ----------------------------
    int result = JOptionPane.showConfirmDialog(
            null,
            panel,
            TEXT_SELECT_INSTANCE,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            icon
    );

    if (result != JOptionPane.OK_OPTION) return;

    String selectedInstance = list.getSelectedValue();
    File mods;

    // ----------------------------
    // 手动选择 mods 目录
    // ----------------------------
    if (selectedInstance.equals(TEXT_MANUAL)) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int r = chooser.showOpenDialog(null);
        if (r != JFileChooser.APPROVE_OPTION) return;
        mods = chooser.getSelectedFile();
    } else {
        mods = instanceMap.get(selectedInstance);
    }

    if (mods == null || !mods.exists()) {
        JOptionPane.showMessageDialog(null, TEXT_MODS_NOT_EXIST);
        return;
    }

    // ----------------------------
    // 获取 mods 目录下所有 jar
    // ----------------------------
    File[] jars = mods.listFiles((d, n) -> n.endsWith(".jar"));
    if (jars == null) return;

    // ----------------------------
    // 替换 MTR 资源
    // ----------------------------
    for (File jar : jars) {
        if (isMTRJar(jar)) {
            label.setText(TEXT_WORKING); // 显示进度
            backupJar(jar);              // 备份原 jar
            replaceJson(jar);            // 替换资源
            count++;
        }
    }

    // ----------------------------
    // 完成提示
    // ----------------------------
    JOptionPane.showMessageDialog(null, TEXT_DONE + count + " MTR jar");

    frame.dispose(); // 关闭窗口
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

    // 需要替换的文件列表
    String[] langFiles = {
            "assets/mtr/mtr_custom_resources.json",
            "assets/mtr/lang/en_us.json",
            "assets/mtr/lang/zh_cn.json",
            "assets/mtr/lang/zh_hk.json",
            "assets/mtr/lang/zh_tw.json",
            "assets/mtr/lang/ja_jp.json"
    };

    while ((entry = zin.getNextEntry()) != null) {

        String name = entry.getName();

        boolean replace = false;

        // 判断是否是需要替换的文件
        for (String lang : langFiles) {
            if (name.equals(lang)) {
                replace = true;
                break;
            }
        }

        if (replace) {

            zout.putNextEntry(new ZipEntry(name));

            String fileName =
                    name.substring(name.lastIndexOf("/") + 1);

            InputStream replaceStream =
                    MTRReplace.class.getResourceAsStream("/" + fileName);

            if (replaceStream != null) {
                replaceStream.transferTo(zout);
            }

        } else {

            // 不是替换文件就原样复制
            zout.putNextEntry(new ZipEntry(name));

            zin.transferTo(zout);

        }

    }

    zin.close();
    zout.close();

    Files.delete(jar.toPath());

    Files.move(temp.toPath(), jar.toPath());

}}