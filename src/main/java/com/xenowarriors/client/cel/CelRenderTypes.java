package com.xenowarriors.client.cel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** The outline pass: an inflated copy of the model drawn with only its back faces (front faces culled), in black. */
public final class CelRenderTypes extends RenderType {
    private static final ResourceLocation WHITE = com.xenowarriors.XenoWarriors.id("textures/misc/xw_white.png");

    private CelRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean c, boolean s, Runnable a, Runnable z) { super(n, f, m, b, c, s, a, z); }

    /** Culls front faces instead of back faces (the "inverted hull" trick). */
    private static final class FrontCull extends RenderStateShard.CullStateShard {
        FrontCull() { super(true); }
        @Override public void setupRenderState() { RenderSystem.enableCull(); GL11.glCullFace(GL11.GL_FRONT); }
        @Override public void clearRenderState() { GL11.glCullFace(GL11.GL_BACK); }
    }

    public static final RenderType OUTLINE = create("xenowarriors_cel_outline", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(WHITE, false, false))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(new FrontCull())
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true));
}
