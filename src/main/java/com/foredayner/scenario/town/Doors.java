package com.foredayner.scenario.town;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.Set;

/**
 * After a town building is placed: every door in an outside wall becomes a double door (a second
 * door is set into the wall beside it), and the 2 blocks in front of and behind the pair are
 * cleared at door height so nothing stands in the way.
 */
public final class Doors {

    private Doors() {
    }

    /** How far in front of and behind the doors is kept free. */
    public static final int FREE = 2;

    /** Fix the doors of the building between min and max (inclusive). Returns how many ways in were found. */
    public static int fix(ServerWorld world, BlockPos min, BlockPos max) {
        Set<BlockPos> done = new HashSet<>();
        int found = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (done.contains(pos) || !lowerDoor(world.getBlockState(pos))) {
                        continue;
                    }
                    BlockState door = world.getBlockState(pos);
                    Direction facing = door.get(DoorBlock.FACING);
                    boolean front = covered(world, pos.offset(facing), max.getY());
                    boolean back = covered(world, pos.offset(facing.getOpposite()), max.getY());
                    com.foredayner.scenario.ScenarioBuilderMod.LOG.info("Door at {} facing {}: covered {} / {}", pos.toShortString(), facing, front, back);
                    if (front == back) {
                        continue; // a door between two rooms (or under a porch roof on both sides)
                    }
                    found++;
                    Direction ccw = facing.rotateYCounterclockwise();
                    BlockPos other = null;
                    for (Direction side : new Direction[] { ccw, ccw.getOpposite() }) {
                        if (lowerDoor(world.getBlockState(pos.offset(side)))) {
                            other = pos.offset(side);
                            break;
                        }
                    }
                    if (other == null) {
                        for (Direction side : new Direction[] { ccw, ccw.getOpposite() }) {
                            BlockPos wall = pos.offset(side);
                            if (wall(world, wall) && wall(world, wall.up())) {
                                other = wall;
                                break;
                            }
                        }
                        if (other != null) {
                            set(world, other, door.getBlock(), facing);
                        }
                    }
                    done.add(pos);
                    com.foredayner.scenario.ScenarioBuilderMod.LOG.info("Door at {}: second door {}", pos.toShortString(), other == null ? "-" : other.toShortString());
                    if (other != null) {
                        done.add(other);
                        // The door on the counter-clockwise side hinges on the left, the other on the right.
                        boolean otherLeft = other.equals(pos.offset(ccw));
                        hinge(world, pos, otherLeft ? DoorHinge.RIGHT : DoorHinge.LEFT);
                        hinge(world, other, otherLeft ? DoorHinge.LEFT : DoorHinge.RIGHT);
                    }
                    for (BlockPos d : other == null ? new BlockPos[] { pos } : new BlockPos[] { pos, other }) {
                        for (Direction dir : new Direction[] { facing, facing.getOpposite() }) {
                            for (int k = 1; k <= FREE; k++) {
                                for (int up = 0; up <= 1; up++) {
                                    BlockPos at = d.offset(dir, k).up(up);
                                    BlockState s = world.getBlockState(at);
                                    if (!s.isAir() && !(s.getBlock() instanceof DoorBlock) && !s.getCollisionShape(world, at).isEmpty()) {
                                        world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    public static boolean lowerDoor(BlockState s) {
        return s.getBlock() instanceof DoorBlock && s.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /** Whether something is overhead (a ceiling or roof) above this spot, up to the top of the building. */
    static boolean covered(ServerWorld world, BlockPos pos, int top) {
        for (int y = pos.getY() + 2; y <= top + 1; y++) {
            BlockPos at = new BlockPos(pos.getX(), y, pos.getZ());
            if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A plain wall block a door can replace (not glass, not another door, not air). */
    static boolean wall(ServerWorld world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        return !s.isAir() && !(s.getBlock() instanceof DoorBlock) && s.isFullCube(world, pos);
    }

    private static void set(ServerWorld world, BlockPos pos, Block block, Direction facing) {
        BlockState lower = block.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        world.setBlockState(pos, lower, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        world.setBlockState(pos.up(), lower.with(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }

    private static void hinge(ServerWorld world, BlockPos lower, DoorHinge hinge) {
        BlockState s = world.getBlockState(lower);
        if (s.getBlock() instanceof DoorBlock) {
            world.setBlockState(lower, s.with(DoorBlock.HINGE, hinge).with(DoorBlock.OPEN, false), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            BlockState u = world.getBlockState(lower.up());
            if (u.getBlock() instanceof DoorBlock) {
                world.setBlockState(lower.up(), u.with(DoorBlock.HINGE, hinge).with(DoorBlock.OPEN, false), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
        }
    }
}
