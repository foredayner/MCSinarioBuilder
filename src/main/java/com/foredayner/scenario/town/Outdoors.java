package com.foredayner.scenario.town;

import com.foredayner.scenario.sheet.Period;
import com.foredayner.scenario.sheet.ScenarioSheet;

import java.util.Locale;

/**
 * What an outdoor place is made of, as a checklist for its kind and its period (like the
 * buildings' checklists): what it MUST have and what it may also have. Also tells whether the
 * scenario asks for the land itself to rise or fall there (a cliff, a hill, a pit); otherwise the
 * ground stays flat.
 */
public final class Outdoors {

    private Outdoors() {
    }

    /** Words that ask for the land to rise or fall. */
    private static final java.util.regex.Pattern TERRAIN = java.util.regex.Pattern.compile(
        "절벽|벼랑|낭떠러지|언덕|동산|비탈|경사|구덩이|웅덩이|연못|못가|늪|고개|둔덕|계곡|골짜기|협곡|바위산|산길|산비탈|오르막|내리막"
            + "|cliff|crag|bluff|hill|slope|ravine|gully|pit|hollow|pond|swamp|marsh|ridge|knoll|bank|quarry|valley");

    /** Whether the scenario asks for the land here to rise or fall. */
    public static boolean terrain(ScenarioSheet.Place p) {
        if ("cave".equals(p.kind)) {
            return true; // a cave mouth is always in a rocky rise
        }
        return TERRAIN.matcher(said(p)).find();
    }

    /** All the place says about itself. */
    static String said(ScenarioSheet.Place p) {
        return ((p.name == null ? "" : p.name) + " " + (p.notes == null ? "" : p.notes) + " " + String.join(" ", p.scenes))
            .toLowerCase(Locale.ROOT);
    }

    static boolean tree(String what) {
        return what.contains("tree") || what.contains("나무") || what.contains("고목") || what.contains("당산") || what.contains("느티")
            || what.contains("willow") || what.contains("버드") || what.contains("은행");
    }

    static boolean cliff(String what) {
        return what.contains("cliff") || what.contains("절벽") || what.contains("전망") || what.contains("overlook") || what.contains("벼랑")
            || what.contains("lookout") || what.contains("viewpoint") || what.contains("낭떠러지");
    }

    /** Which of the checklists below fits the place. */
    public static String type(ScenarioSheet.Place p) {
        String what = said(p);
        if (!"woods".equals(p.kind) && tree(what)) {
            return "tree";
        }
        if (cliff(what)) {
            return "cliff";
        }
        return p.kind;
    }

    /** The checklist for this place in this period. */
    public static String checklist(ScenarioSheet.Place p, String period) {
        boolean east = Period.east(period);
        boolean other = Period.OTHER.equals(period);
        boolean now = Period.contemporary(period) || Period.EAST_POSTWAR.equals(period);
        boolean old = Period.medieval(period);
        String must;
        String also;
        switch (type(p)) {
            case "tree" -> {
                must = "ONE great old tree as the centre, made with ai.tree (height 16-22, trunk 3, an old kind: "
                    + (east ? "oak or dark_oak for a 느티나무/당산나무, cherry only if the scenario says so" : "oak, dark_oak or willow")
                    + "), standing in the middle of the site; the ground round its foot left open so people can stand under it";
                also = east ? "a 금줄 (a straw rope: a ring of white_wool or white_carpet with string or chains round the trunk at 2-3 high),"
                    + " a stone altar or a low stone table at its foot, little stone towers (돌탑: cobblestone_wall and stone buttons),"
                    + " coloured cloth strips (wool) on low branches, a low stone wall ring or bench round it, a path to it"
                    : "carvings in the trunk (a few stripped logs or a sign), a bench or low wall round it, lanterns hung from"
                    + " branches by chains, offerings or a small plaque, a path to it, a few smaller trees and bushes around";
            }
            case "cliff" -> {
                must = "a high rocky edge: the land rises toward the back of the site (ai.rock and ai.mound, overlapping, 6-12 high)"
                    + " and drops away behind; a path that climbs to a flat lookout on top; a railing along the edge ("
                    + (old || other ? "wooden fences" : east && !now ? "wooden fences" : "iron bars or fences") + ")";
                also = "steps cut into the slope (stone stairs and slabs), a bench and a lamp or lantern post at the lookout, a small"
                    + " sign, wind-bent trees (ai.tree, small, leaning) and bushes on the slopes, loose rocks by the path, tall grass";
            }
            case "cemetery" -> {
                if (east && !now) {
                    must = "several burial mounds (ai.mound 3-4 wide, 2 high, grass_block) in loose rows on the site, each with a small"
                        + " stone tablet (a stone or andesite block, or a wall block) and a stone offering table (a slab on a block) in front";
                    also = "stone lanterns (stone wall + lantern), a pair of stone posts at the way in, pine trees (ai.tree pine) round the"
                        + " edge, a low earth or stone wall at the back, grass paths between the mounds, tall grass";
                } else if (east) {
                    must = "a hillside public graveyard (공원묘지): rows of grave plots on terraces, each a low stone border with a"
                        + " polished stone (polished_andesite or polished_diorite) headstone, gravel paths between the rows";
                    also = "a gate or sign at the entrance, a few pine trees, stone benches, flowers by some graves, a small shelter";
                } else if (now) {
                    must = "a lawn cemetery: rows of low flat or upright headstones (polished stone) on short grass, a paved path"
                        + " and an entrance gate";
                    also = "a few trees and benches, flowers at some graves, a small chapel or a columbarium wall, lamps";
                } else {
                    must = "rows of weathered gravestones of different shapes (stone, cobblestone wall, mossy stone; some leaning or"
                        + " broken), a low stone wall round the site with an iron gate at the front, a gravel path between the rows";
                    also = "tall monuments and stone crosses, a crypt or mausoleum with a door, a big old or dead tree (ai.tree), iron"
                        + " lanterns, overgrown grass, moss and ivy, an open grave";
                }
            }
            case "woods" -> {
                must = "a dense patch of forest: many trees made with ai.tree (8-20 of them, different kinds and heights, mostly "
                    + (east ? "pine, oak and dark_oak" : "oak, spruce, birch and dark_oak") + "), close together, with a winding path"
                    + " into a small clearing in the middle";
                also = "fallen logs, mossy rocks (ai.rock mossy), bushes (ai.bush), ferns and tall grass, mushrooms, moss carpet patches, "
                    + (east ? "a stone cairn or a straw rope on one tree in the clearing" : old ? "an old standing stone in the clearing"
                        : "a hunters' seat or a fallen sign in the clearing");
            }
            case "square" -> {
                if (east && !now) {
                    must = "a village common of packed earth with a big old tree (ai.tree, a 느티나무) and a stone-ringed village well"
                        + " (a ring of stone, a wooden frame and a bucket)";
                    also = "a 평상 platform under the tree, a stone bench ring, jars, a notice post, a few smaller trees";
                } else if (now) {
                    must = "a paved plaza (stone bricks or smooth stone in a pattern) with benches and street lamps, trees in planters";
                    also = "a fountain or a monument without figures, a notice board, flower beds, a bus stop or a phone box";
                } else {
                    must = "a town square paved with cobblestone and stone bricks in a pattern, a stone fountain or a well in the middle";
                    also = "benches, lamp posts, a market stall or two, flower beds, a few trees, a notice board";
                }
            }
            case "dock" -> {
                must = "a pier of planks on log posts reaching out over the water, with mooring posts and a boat tied up";
                also = "piles of crates and barrels, coils of rope (chains), fishing nets (cobwebs), a lantern post, a shed or a"
                    + (east ? " fish-drying rack (fences and slabs)" : " small harbour office") + ", a crane beam";
            }
            case "beach" -> {
                must = "a strip of sand down to the water with rocks (ai.rock) and tide pools, an upturned rowing boat";
                also = "driftwood logs, a few posts and a torn net, dune grass, shells (bone buttons), a path down from the town";
            }
            case "cave" -> {
                must = "a rocky rise (ai.rock and ai.mound, 6-10 high) with a dark cave mouth 3 wide going in and down";
                also = "moss and hanging roots over the mouth, loose rocks and gravel at the foot, a path to it, "
                    + (east ? "a straw rope or a small altar at the entrance" : "old timber props or a warning sign at the entrance");
            }
            case "scene" -> {
                must = "the road or path across the site (gravel, dirt_path) with its verges, and the things that show what happened"
                    + " here, each separate (a dropped bundle, a broken basket or cart wheel, scattered stones, a torn cloth on a bush)";
                also = "a ditch, a fence or low wall along the road, bushes and a tree or two (ai.tree, ai.bush), a milestone or signpost";
            }
            case "landmark" -> {
                must = "the " + p.name + " itself as the centre, well made and big enough to read from afar, as it looks in the setting";
                also = "a path to it, stones and plants round it, a low wall or fence, a sign";
            }
            case "pyeongsang" -> {
                must = "a Korean 평상: a low, wide wooden platform (about 4x3, 1 block high: slabs on fence posts) with a straw mat and"
                    + " a small low table, under a big shady tree (ai.tree)";
                also = "a path, jars and pots, a fan or a basket on the platform";
            }
            default -> {
                must = "a fitting scene for " + p.kind + " \"" + p.name + "\"";
                also = "trees (ai.tree), rocks (ai.rock), bushes (ai.bush), paths and small details that fit it";
            }
        }
        return "MUST HAVE: " + must + ".\nMAY ALSO HAVE: " + also + ".";
    }
}
