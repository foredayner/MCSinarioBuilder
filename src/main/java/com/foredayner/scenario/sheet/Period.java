package com.foredayner.scenario.sheet;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The period and culture a town is built in, which picks the building checklists, facade rules,
 * reference cards, yards and the ground (streets and lights). The AI picks it when it reads the
 * scenario; the keeper can change it on the sheet. {@link #OTHER} (fantasy, science fiction...)
 * gets no checklist: the town follows only its architecture guide and the place notes.
 */
public final class Period {

    public static final String WEST_MEDIEVAL = "west_medieval";
    /** Western 1800s to the 1950s (the classic 1920s Call of Cthulhu town). */
    public static final String WEST_MODERN = "west_modern";
    /** Western 1960s to now. */
    public static final String WEST_CONTEMPORARY = "west_contemporary";
    /** East Asian before the 1900s (Joseon, Edo...). */
    public static final String EAST_MEDIEVAL = "east_medieval";
    /** East Asian 1900s to 1945 (Korea under Japanese rule). */
    public static final String EAST_MODERN = "east_modern";
    /** East Asian 1945 to the 1980s (Korea of the 1950s-60s). */
    public static final String EAST_POSTWAR = "east_postwar";
    /** East Asian 1990s to now. */
    public static final String EAST_CONTEMPORARY = "east_contemporary";
    /** Fantasy, science fiction or anything else: no checklist. */
    public static final String OTHER = "other";

    public static final List<String> ALL = List.of(WEST_MEDIEVAL, WEST_MODERN, WEST_CONTEMPORARY, EAST_MEDIEVAL, EAST_MODERN,
        EAST_POSTWAR, EAST_CONTEMPORARY, OTHER);

    private Period() {
    }

    public static String label(String period) {
        return switch (period == null ? "" : period) {
            case WEST_MEDIEVAL -> "서양 중세";
            case WEST_MODERN -> "서양 근대";
            case WEST_CONTEMPORARY -> "서양 현대";
            case EAST_MEDIEVAL -> "동양 중세";
            case EAST_MODERN -> "동양 근대";
            case EAST_POSTWAR -> "동양 근현대";
            case EAST_CONTEMPORARY -> "동양 현대";
            default -> "기타 (판타지·SF)";
        };
    }

    /** What the keeper sees on hover. */
    public static String description(String period) {
        return switch (period == null ? "" : period) {
            case WEST_MEDIEVAL -> "중세 유럽: 목골조·돌집, 초가·슬레이트 지붕, 흙길, 등불 거의 없음";
            case WEST_MODERN -> "1800년대~1950년대 서양 (1920년대 크툴루의 부름): 현관과 덧창, 굴뚝, 자갈길, 가스등";
            case WEST_CONTEMPORARY -> "1960년대 이후 서양: 유리와 콘크리트, 아스팔트 도로, 가로등";
            case EAST_MEDIEVAL -> "조선·에도 등 1900년 이전 동양: 한옥과 초가, 기와지붕, 돌담, 흙길";
            case EAST_MODERN -> "1900~1945 동양 (일제강점기): 한옥과 붉은 벽돌 관공서, 경성 상가, 자갈길, 나무 전봇대";
            case EAST_POSTWAR -> "1945~1980년대 동양 (1950~60년대 한국): 슬레이트·함석지붕, 시멘트 블록집, 구멍가게, 산골은 너와집, 비포장길";
            case EAST_CONTEMPORARY -> "1990년대 이후 동양: 빌라, 편의점, 파출소, 교회, 아스팔트 도로, 가로등";
            default -> "판타지·SF 등: 체크리스트 없이 건축 가이드와 장소 메모만 따라요";
        };
    }

    public static boolean known(String period) {
        return ALL.contains(period);
    }

    public static boolean east(String period) {
        return period != null && period.startsWith("east_");
    }

    public static boolean west(String period) {
        return period != null && period.startsWith("west_");
    }

    /** Asphalt, sidewalks, street lights. */
    public static boolean contemporary(String period) {
        return WEST_CONTEMPORARY.equals(period) || EAST_CONTEMPORARY.equals(period);
    }

    public static boolean medieval(String period) {
        return WEST_MEDIEVAL.equals(period) || EAST_MEDIEVAL.equals(period);
    }

    private static final Pattern YEAR = Pattern.compile("(1[0-9]|20|21|22|23|24|25|26|27|28|29)(\\d)0");

    /**
     * A best guess from the sheet's words, for sheets without a period (older sheets, or an answer
     * that left it out). The keeper can change it.
     */
    public static String guess(String era, String setting, String title, String style) {
        String t = (era + " " + setting + " " + title + " " + style).toLowerCase(Locale.ROOT);
        String e = era == null ? "" : era.toLowerCase(Locale.ROOT);
        if (has(t, "fantasy", "판타지", "sci-fi", "science fiction", "space station", "우주", "미래", "future", "cyberpunk", "사이버펑크",
            "dungeon", "elf", "엘프", "마법 왕국")) {
            return OTHER;
        }
        boolean east = has(t, "korea", "한국", "조선", "joseon", "chosen", "경성", "gyeongseong", "keijo", "서울", "seoul", "부산", "busan",
            "강원", "gangwon", "경기도", "gyeonggi", "인천", "대구", "대전", "광주", "japan", "일본", "edo", "에도", "tokyo", "도쿄", "kyoto",
            "교토", "china", "중국", "청나라", "qing", "ming", "고려", "goryeo", "hanok", "한옥", "taiwan", "대만", "hong kong", "홍콩");
        boolean medieval = has(t, "medieval", "중세", "고대", "ancient", "조선시대", "조선 시대", "joseon dynasty", "고려", "삼국", "에도 시대",
            "edo period", "middle ages", "feudal", "봉건");
        boolean nowWords = has(e, "modern", "현대", "contemporary", "present", "today", "현재");
        int decade = -1;
        for (String where : new String[] { e, t }) {
            Matcher m = YEAR.matcher(where);
            if (m.find()) {
                decade = Integer.parseInt(m.group(1) + m.group(2) + "0");
                break;
            }
        }
        if (decade >= 2100) {
            return OTHER;
        }
        if (east) {
            if (medieval || decade >= 0 && decade < 1900) {
                return EAST_MEDIEVAL;
            }
            boolean colonial = has(t, "일제", "colonial", "경성", "gyeongseong", "keijo");
            if (decade >= 1900 && decade < 1945 || colonial) {
                return EAST_MODERN;
            }
            if (decade < 0 && has(t, "조선", "joseon")) {
                return EAST_MEDIEVAL; // Joseon with no year and no colonial words: the dynasty
            }
            if (decade >= 1945 && decade < 1990) {
                return EAST_POSTWAR;
            }
            return EAST_CONTEMPORARY;
        }
        if (medieval || decade >= 0 && decade < 1800) {
            return WEST_MEDIEVAL;
        }
        if (decade >= 1960 || decade < 0 && nowWords) {
            return WEST_CONTEMPORARY;
        }
        return WEST_MODERN;
    }

    private static boolean has(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
