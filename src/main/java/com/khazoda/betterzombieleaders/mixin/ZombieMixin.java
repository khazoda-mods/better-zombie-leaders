package com.khazoda.betterzombieleaders.mixin;

import com.khazoda.betterzombieleaders.LeaderZombie;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
abstract class ZombieMixin {
  @ModifyExpressionValue(method = "handleAttributes", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextFloat()F"))
  private float betterzombieleaders$requireHeadroom(float roll, float difficultyModifier, EntitySpawnReason spawnReason) {
    if (roll >= difficultyModifier * 0.05F) return roll;
    Zombie zombie = (Zombie) (Object) this;
    AABB box = zombie.getBoundingBox();
    // check for 0.5 blocks of headroom and don't apply leader attributes if it would suffocate
    AABB headroom = new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + box.getYsize() * 0.25, box.maxZ);
    return zombie.level().noBlockCollision(zombie, headroom) ? roll : 1.0F;
  }

  @Inject(method = "handleAttributes", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/zombie/Zombie;setCanBreakDoors(Z)V"))
  private void betterzombieleaders$updateLeaderScale(float difficultyModifier, EntitySpawnReason spawnReason, CallbackInfo ci) {
    LeaderZombie.updateScale((Zombie) (Object) this);
  }

  @ModifyReturnValue(method = "getBaseExperienceReward", at = @At("RETURN"))
  private int betterzombieleaders$increaseLeaderExperience(int experience) {
    Zombie zombie = (Zombie) (Object) this;
    double strength = LeaderZombie.strength(zombie);
    return strength > 0.0 ? LeaderZombie.randomRounded(zombie, experience * (1.0 + strength)) : experience;
  }
}
