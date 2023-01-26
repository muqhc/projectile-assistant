package io.github.muqhc.projectileassistant

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Vec2f
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.*

class ProjectileAssistantMod : ModInitializer {
    var entitiesinSight: List<Entity> = listOf()
    lateinit var targetingKey: KeyBinding
    var target: Entity? = null

    override fun onInitialize() {
        client = MinecraftClient.getInstance()
        mainProcess()
    }

    private fun mainProcess() {
        targetingKey = KeyBindingHelper.registerKeyBinding(KeyBinding(
            "key.myshader.targeting",
            InputUtil.GLFW_KEY_G,
            "category.myshader.keybinding"
        ))
        HudRenderCallback.EVENT.register(SensorRendering())
        ClientTickEvents.END_CLIENT_TICK.register {
            onUpdate()
        }
    }

    private var targetingKeyPressed = false
    fun onUpdate() {
        entitiesinSight = client.world?.entities?.toList()?.filter { client.player!!.canSee(it) && client.player != it } ?: listOf()
        if (targetingKey.isPressed && !targetingKeyPressed) {
            targetingKeyPressed = true
            target = targeting()
        }
        else if (!targetingKey.isPressed) {
            targetingKeyPressed = false
        }
    }

    fun targeting(): Entity? {
        val selfDir = client.player?.rotationVecClient?.toVector3f()?.normalize(Vector3f(0f,0f,0f)) ?: return null
        return entitiesinSight.filter { (it != target) && it.canHit() }
            .filter {
                val vecToTarget = it.eyePos.toVector3f().sub(client.player!!.pos.toVector3f(), Vector3f(0f,0f,0f))
                val dirToTarget = vecToTarget.add(Vector3f(0f,0f,0f),Vector3f(0f,0f,0f)).normalize()
                val horizon = Vector2f(selfDir.x,selfDir.z).angle(Vector2f(dirToTarget.x,dirToTarget.z))
                abs(horizon) < (PI/2)
            }
            .minByOrNull {
                val vecToTarget = it.eyePos.toVector3f().sub(client.player!!.pos.toVector3f(), Vector3f(0f,0f,0f))
                val distance = vecToTarget.cross(selfDir, Vector3f(0f,0f,0f))
                distance.lengthSquared()
            }
    }

    fun calcShootDir(target: Entity, a: Double, g: Double): Pair<Double,Double>? {
        val loc = target.pos.toVector3f().sub(client.player!!.pos.toVector3f(),Vector3f(0f,0f,0f))

        val qSquared = (loc.x*loc.x) + (loc.z*loc.z)
        val q = sqrt(qSquared).toDouble()
        val p = loc.y.toDouble()

//        val a = 62.8
//        val g = 30.0

        val vy1 = -((0.707107*sqrt(((a.pow(2)*q.pow(2)-sqrt(-q.pow(4)*(-a.pow(4)+2*a.pow(2)*g*p+g.pow(2)*q.pow(2)))-g*p*q.pow(2))/(p.pow(2)+q.pow(2))))*(a.pow(2)*q.pow(2)+sqrt(-q.pow(4)*(-a.pow(4)+2*a.pow(2)*g*p+g.pow(2)*q.pow(2)))))/(g*q.pow(3)))
        val vy2 = -((0.707107*sqrt(((a.pow(2)*q.pow(2)+sqrt(-q.pow(4)*(-a.pow(4)+2*a.pow(2)*g*p+g.pow(2)*q.pow(2)))-g*p*q.pow(2))/(p.pow(2)+q.pow(2))))*(a.pow(2)*q.pow(2)-sqrt(-q.pow(4)*(-a.pow(4)+2*a.pow(2)*g*p+g.pow(2)*q.pow(2)))))/(g*q.pow(3)))
        val vx1 = -0.707107*sqrt(((a.pow(2)*q.pow(2)-g*p*q.pow(2)-sqrt(-q.pow(4)*(-a.pow(4)+2*g*p*a.pow(2)+g.pow(2)*q.pow(2))))/(p.pow(2)+q.pow(2))))
        val vx2 = -0.707107*sqrt(((a.pow(2)*q.pow(2)-g*p*q.pow(2)+sqrt(-q.pow(4)*(-a.pow(4)+2*g*p*a.pow(2)+g.pow(2)*q.pow(2))))/(p.pow(2)+q.pow(2))))

        if (vy1.isNaN()) return null


        return Pair(
            vy1/vx1,
            vy2/vx2
        )
    }

    fun TextRenderer.indicateDir(
        matrixStack: MatrixStack?, entity: Entity,
        frontText: String = "'", backText: String = ".",
        frontColor: Int = 0xFFDDDD, backColor: Int = 0xDDDDFF,
    ) {
        val renderer = this

        val centerX = client.window.scaledWidth/2
        val centerY = client.window.scaledHeight/2

        val selfDir = client.player!!.rotationVecClient.toVector3f().normalize(Vector3f(0f,0f,0f))
        val dirToTarget = entity.eyePos.toVector3f().sub(client.player!!.pos.toVector3f(), Vector3f(0f, 0f, 0f)).normalize()
        val horizon = Vector2f(selfDir.x,selfDir.z).angle(Vector2f(dirToTarget.x,dirToTarget.z))
        if (abs(horizon) < (PI/2)) renderer.draw(
            matrixStack, frontText,
            (atan(horizon)*(centerX/(PI/2))-(renderer.getWidth(".")/2)+centerX).toFloat(),
            (centerY*(13.0/8.0)).toFloat(),
            frontColor
        )
        else renderer.draw(
            matrixStack, backText,
            (atan(
                (PI/2) - (abs(horizon) - (PI/2))
            )*sign(horizon)*(centerX/(PI/2))-(renderer.getWidth("'")/2)+centerX).toFloat(),
            (centerY*(13.0/8.0)).toFloat(),
            backColor
        )
    }

    inner class SensorRendering : HudRenderCallback {
        override fun onHudRender(matrixStack: MatrixStack?, tickDelta: Float) {
            val renderer = client.inGameHud.textRenderer

            val stateStack = mutableListOf<Pair<String,Int>>()

            stateStack += (target?.name?.string ?: "<no target>") to 0xFFFFFF
            if (target != null) {
                if (target is LivingEntity && target!!.isAlive)
                    stateStack += "|".repeat((target!! as LivingEntity).health.toInt()) to 0xFFDDDD
                if (target!!.isRemoved)
                    stateStack += "[Dead]" to 0xFF6666
            }

            stateStack.forEachIndexed { index, (text,color) ->
                renderer.draw(matrixStack,text,0f,(renderer.fontHeight*index).toFloat(),color)
            }

            val centerX = client.window.scaledWidth/2
            val centerY = client.window.scaledHeight/2
            val center = Vec2f(centerX.toFloat(),centerY.toFloat())


            if (client.player == null) return

            val selfDir = client.player!!.rotationVecClient.toVector3f().normalize(Vector3f(0f,0f,0f))
            entitiesinSight.filter { it.canHit() && (it !is PlayerEntity) }.forEach {
                renderer.indicateDir(matrixStack,it)
            }
            entitiesinSight.filter { it.canHit() && (it is PlayerEntity) }.forEach {
                renderer.indicateDir(matrixStack,it,
                    frontText = "^", backText = "v", frontColor = 0xFF6666, backColor = 0x6666DD
                )
            }

            targeting()?.let {
                val dirToTarget = it.eyePos.toVector3f().sub(client.player!!.pos.toVector3f(), Vector3f(0f,0f,0f)).normalize()
                val horizon = Vector2f(selfDir.x,selfDir.z).angle(Vector2f(dirToTarget.x,dirToTarget.z))
                renderer.indicateDir(matrixStack,it, frontText = "+", backText = "+")
                renderer.draw(
                    matrixStack, it.name,
                    (centerX-(renderer.getWidth(it.name)/2)).toFloat(),
                    (centerY*(11.0/8.0)).toFloat(),
                    0xFFFFFF
                )
                if (it is LivingEntity)
                    renderer.draw(
                        matrixStack,"|".repeat(it.health.toInt()),
                        (centerX-(renderer.getWidth("|".repeat(it.health.toInt()))/2)).toFloat(),
                        (centerY*(11.0/8.0)).toFloat() + renderer.fontHeight,
                        0xFFDDDD
                    )
                it
            }


            if (target == null) return

            val dirToTarget = target!!.eyePos.toVector3f().sub(client.player!!.pos.toVector3f(), Vector3f(0f,0f,0f)).normalize()
            val horizon = Vector2f(selfDir.x,selfDir.z).angle(Vector2f(dirToTarget.x,dirToTarget.z))

            renderer.indicateDir(matrixStack,target!!, frontText = "X", backText = "X")

            val arrowOrbits = calcShootDir(target!!,62.8,30.0)?.toList()
                ?: return Unit.also {
                    renderer.draw(
                        matrixStack, "[Too Far]",
                        (atan(horizon)*(centerX/(PI/4))-(renderer.getWidth("[Too Far]")/2)+centerX).toFloat(),
                        -(dirToTarget.y-selfDir.y)*centerY+centerY,
                        0xFFBBBB
                    )
                }
            val snowOrbits = calcShootDir(target!!,26.0,12.0)?.toList() ?: listOf<Double>().also {
                renderer.draw(
                    matrixStack, "[Too Far for Snowball]",
                    (atan(horizon)*(centerX/(PI/4))-(renderer.getWidth("[Too Far Snowball]")/2)+centerX).toFloat(),
                    (centerY).toFloat(),
                    0xDDFFBB
                )
            }

            val colorSelector = listOf(
                0x0000FF,
                0x4444FF,
                0x00FF88,
                0x44FF88,
            )

            (arrowOrbits+snowOrbits).forEachIndexed { index, it ->
                val point = center.add(
                    Vec2f(
                        atan(horizon)*(centerX/(PI/4)).toFloat(),
                        atan((-atan(it) - (client.player!!.pitch* PI/180))).toFloat()*(centerY/(PI/4)).toFloat()
                    )
                )
                renderer.draw(
                    matrixStack,"[+]",
                    point.x-(renderer.getWidth("[+]")/2),
                    point.y-(renderer.fontHeight/2),
                    colorSelector[index]
                )
            }
        }
    }

    companion object {
        lateinit var client: MinecraftClient
    }
}