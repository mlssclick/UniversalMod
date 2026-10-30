package universalmod.setting.bind;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import universalmod.integration.minecraft.IMinecraft;

public class KeyStorage implements IMinecraft {
    public static final Map<String, Integer> keyMap = new HashMap<>();
    private static final Map<Integer, String> reverseKeyMap = new HashMap<>();

    public static String getKey(int integer) {
        return getReverseKey(integer);
    }

    public static String getReverseKey(int key) {
        return reverseKeyMap.getOrDefault(key, "");
    }

    public static Integer getKey(String key) {
        return keyMap.getOrDefault(key, -1);
    }

    private static void putMappings() {
        for (char c = 'A'; c <= 'Z'; c++) {
            keyMap.put(String.valueOf(c), 65 + (c - 'A'));
        }

        for (char c = '0'; c <= '9'; c++) {
            keyMap.put(String.valueOf(c), 48 + (c - '0'));
        }

        for (int i = 1; i <= 12; i++) {
            keyMap.put("F" + i, 290 + (i - 1));
        }

        keyMap.put("MOUSE1", 0);
        keyMap.put("MOUSE2", 1);
        keyMap.put("MOUSE3", 2);
        keyMap.put("MOUSE4", 3);
        keyMap.put("MOUSE5", 4);
        keyMap.put("MOUSE6", 5);
        keyMap.put("MOUSE7", 6);
        keyMap.put("MOUSE8", 7);
        keyMap.put("NUMPAD1", 321);
        keyMap.put("NUMPAD2", 322);
        keyMap.put("NUMPAD3", 323);
        keyMap.put("NUMPAD4", 324);
        keyMap.put("NUMPAD5", 325);
        keyMap.put("NUMPAD6", 326);
        keyMap.put("NUMPAD7", 327);
        keyMap.put("NUMPAD8", 328);
        keyMap.put("NUMPAD9", 329);
        keyMap.put("NUMPAD_DECIMAL", 330);
        keyMap.put("NUMPAD_DIVIDE", 331);
        keyMap.put("NUMPAD_MULTIPLY", 332);
        keyMap.put("NUMPAD_SUBTRACT", 333);
        keyMap.put("NUMPAD_ADD", 334);
        keyMap.put("NUMPAD_ENTER", 335);
        keyMap.put("NUMPAD_EQUAL", 336);
        keyMap.put("SPACE", 32);
        keyMap.put("ENTER", 257);
        keyMap.put("ESCAPE", 256);
        keyMap.put("HOME", 268);
        keyMap.put("INSERT", 260);
        keyMap.put("DELETE", 261);
        keyMap.put("END", 269);
        keyMap.put("PAGEUP", 266);
        keyMap.put("PAGEDOWN", 267);
        keyMap.put("RIGHT", 262);
        keyMap.put("LEFT", 263);
        keyMap.put("DOWN", 264);
        keyMap.put("UP", 265);
        keyMap.put("RSHIFT", 344);
        keyMap.put("R_SHIFT", 344);
        keyMap.put("RIGHTSHIFT", 344);
        keyMap.put("RIGHT_SHIFT", 344);
        keyMap.put("LSHIFT", 340);
        keyMap.put("RCTRL", 345);
        keyMap.put("LCTRL", 341);
        keyMap.put("RALT", 346);
        keyMap.put("LALT", 342);
        keyMap.put("RSUPER", 347);
        keyMap.put("LSUPER", 343);
        keyMap.put("MENU", 348);
        keyMap.put("CAPS_LOCK", 280);
        keyMap.put("NUM_LOCK", 282);
        keyMap.put("SCROLL_LOCK", 281);
        keyMap.put("PRINT_SCREEN", 283);
        keyMap.put("APOSTROPHE", 39);
        keyMap.put("SLASH", 47);
        keyMap.put("MINUS", 45);
        keyMap.put("EQUAL", 61);
        keyMap.put("BACKSPACE", 259);
        keyMap.put("BACKSLASH", 92);
        keyMap.put("PERIOD", 46);
        keyMap.put("COMMA", 44);
        keyMap.put("PAUSE", 284);
        keyMap.put("GRAVE", 96);
    }

    private static void reverseMappings() {
        for (Entry<String, Integer> entry : keyMap.entrySet()) {
            reverseKeyMap.put(entry.getValue(), entry.getKey());
        }
    }

    static {
        putMappings();
        reverseMappings();
    }
}
