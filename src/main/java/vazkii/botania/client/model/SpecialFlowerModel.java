/**
 * This class was created by <williewillus>. It's distributed as
 * part of the Botania Mod. Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 * <p>
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.IUnbakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.model.ItemOverrideList;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.client.model.SimpleModelState;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.common.property.IExtendedBlockState;
import org.apache.commons.lang3.tuple.Pair;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.BotaniaAPIClient;
import vazkii.botania.api.state.BotaniaStateProps;
import vazkii.botania.common.item.block.ItemBlockSpecialFlower;

import javax.annotation.Nonnull;
import javax.vecmath.Matrix4f;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

public class SpecialFlowerModel implements IUnbakedModel {
	private static final ModelResourceLocation MISSING = new ModelResourceLocation("builtin/missing", "missing");
	private final ModelResourceLocation baseModel;
	private final ImmutableMap<ResourceLocation, ModelResourceLocation> blockModels;
	private final ImmutableMap<ResourceLocation, ModelResourceLocation> itemModels;

	private SpecialFlowerModel(ModelResourceLocation baseModel,
								ImmutableMap<ResourceLocation, ModelResourceLocation> blockModels,
								ImmutableMap<ResourceLocation, ModelResourceLocation> itemModels) {
		this.baseModel = baseModel;
		this.blockModels = blockModels;
		this.itemModels = itemModels;
	}

	@Override
	public Collection<ResourceLocation> getDependencies() {
		ImmutableSet.Builder<ResourceLocation> builder = ImmutableSet.builder();
		if(!MISSING.equals(baseModel))
			builder.add(baseModel);
		builder.addAll(blockModels.values());
		builder.addAll(itemModels.values());

		// Force island models to be loaded and baked. See FloatingFlowerModel.
		builder.addAll(BotaniaAPIClient.getRegisteredIslandTypeModels().values());

		return builder.build();
	}

	@Override
	public Collection<ResourceLocation> getTextures(Function<ResourceLocation, IUnbakedModel> modelGetter, Set<String> errors) {
		return ImmutableList.of();
	}

	@Override
	public IBakedModel bake(Function<ResourceLocation, IUnbakedModel> modelGetter, Function<ResourceLocation, TextureAtlasSprite> spriteGetter, IModelState state, boolean uvlock, VertexFormat format) {
		ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms = PerspectiveMapWrapper.getTransforms(state);
		IModelState transformState = new SimpleModelState(transforms);

		IBakedModel baseModelBaked = ModelLoaderRegistry.getModelOrMissing(baseModel)
				.bake(modelGetter, spriteGetter, state, uvlock, format);

		ImmutableMap.Builder<ResourceLocation, IBakedModel> bakedBlockBuilder = ImmutableMap.builder();
		for(Map.Entry<ResourceLocation, ModelResourceLocation> e : blockModels.entrySet()) {
			IModel model = ModelLoaderRegistry.getModelOrMissing(e.getValue());
			if(model != ModelLoaderRegistry.getMissingModel())
				bakedBlockBuilder.put(e.getKey(), model.bake(modelGetter, spriteGetter, state, uvlock, format));
		}

		ImmutableMap.Builder<ResourceLocation, IBakedModel> bakedItemBuilder = ImmutableMap.builder();
		for(Map.Entry<ResourceLocation, ModelResourceLocation> e : itemModels.entrySet()) {
			IModel model = ModelLoaderRegistry.getModelOrMissing(e.getValue());
			if(model != ModelLoaderRegistry.getMissingModel())
				bakedItemBuilder.put(e.getKey(), model.bake(modelGetter, spriteGetter, state, uvlock, format));
		}

		return new SpecialFlowerBakedModel(baseModelBaked, bakedBlockBuilder.build(), bakedItemBuilder.build(), transforms);
	}

	@Override
	public IModelState getDefaultState() {
		return TRSRTransformation.identity();
	}

	@Override
	public IUnbakedModel process(ImmutableMap<String, String> customData) {
		// Load the base variant from blockstate json, and also add all the model paths we received from external API

		ModelResourceLocation base = baseModel;
		if(customData.containsKey("base"))
			// Forge blockstate gives custom data in json form, have to drop the quotes
			base = new ModelResourceLocation(customData.get("base").substring(1, customData.get("base").length() - 1));

		ImmutableMap<ResourceLocation, ModelResourceLocation> blockModels = ImmutableMap.copyOf(BotaniaAPIClient.getRegisteredSubtileBlockModels());
		ImmutableMap<ResourceLocation, ModelResourceLocation> itemModels = ImmutableMap.copyOf(BotaniaAPIClient.getRegisteredSubtileItemModels());
		return new SpecialFlowerModel(base, blockModels, itemModels);
	}

	public enum Loader implements ICustomModelLoader {
		INSTANCE {
			@Override
			public void onResourceManagerReload(@Nonnull IResourceManager resourceManager) {
			}

			@Override
			public boolean accepts(ResourceLocation modelLocation) {
				return modelLocation.getNamespace().equals("botania_special") && (
						modelLocation.getPath().equals("specialflower") ||
						modelLocation.getPath().equals("models/block/specialflower") ||
						modelLocation.getPath().equals("models/item/specialflower"));
			}

			@Override
			public IUnbakedModel loadModel(ResourceLocation modelLocation) {
				// Load a dummy model for now, all actual blockModels added in process().
				return new SpecialFlowerModel(new ModelResourceLocation("builtin/missing", "missing"), ImmutableMap.of(), ImmutableMap.of());
			}
		}
	}

	private static class SpecialFlowerBakedModel implements IBakedModel {
		private final IBakedModel baseModel;
		private final ImmutableMap<ResourceLocation, IBakedModel> bakedBlockModels;
		private final ImmutableMap<ResourceLocation, IBakedModel> bakedItemModels;
		private final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;

		SpecialFlowerBakedModel(IBakedModel baseModel,
								ImmutableMap<ResourceLocation, IBakedModel> bakedBlockModels,
								ImmutableMap<ResourceLocation, IBakedModel> bakedItemModels,
								ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> cameraTransforms) {
			this.baseModel = baseModel;
			this.bakedBlockModels = bakedBlockModels;
			this.bakedItemModels = bakedItemModels;
			this.transforms = cameraTransforms;
		}

		@Nonnull
		@Override
		public List<BakedQuad> getQuads(IBlockState state, EnumFacing face, Random rand) {
			IExtendedBlockState extendedState = (IExtendedBlockState) state;
			ResourceLocation subtileId = extendedState.getValue(BotaniaStateProps.SUBTILE_ID);

			IBakedModel model = bakedBlockModels.get(subtileId == null ? BotaniaAPI.DUMMY_SUBTILE_NAME : subtileId);
			if(model == null)
				model = Minecraft.getInstance().getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel();

			return model.getQuads(state, face, rand);
		}

		@Nonnull
		@Override
		public ItemOverrideList getOverrides() {
			return itemHandler;
		}

		private final ItemStack roseFallback = new ItemStack(Blocks.POPPY);

		private final ItemOverrideList itemHandler = new ItemOverrideList() {
			@Nonnull
			@Override
			public IBakedModel getModelWithOverrides(@Nonnull IBakedModel original, ItemStack stack, World world, EntityLivingBase living) {
				IBakedModel model = bakedItemModels.get(ItemBlockSpecialFlower.getType(stack));

				if(model == null)
					model = bakedBlockModels.get(ItemBlockSpecialFlower.getType(stack));
				if(model == null)
					model = Minecraft.getInstance().getItemRenderer().getItemModelMesher().getItemModel(roseFallback);

				return model;
			}
		};

		@Override
		public boolean isAmbientOcclusion() {
			return baseModel.isAmbientOcclusion();
		}

		@Override
		public boolean isGui3d() {
			return baseModel.isGui3d();
		}

		@Override
		public boolean isBuiltInRenderer() {
			return baseModel.isBuiltInRenderer();
		}

		@Nonnull
		@Override
		public TextureAtlasSprite getParticleTexture() {
			return baseModel.getParticleTexture();
		}

		@Nonnull
		@Override
		public ItemCameraTransforms getItemCameraTransforms() {
			return baseModel.getItemCameraTransforms();
		}

		@Override
		public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType cameraTransformType) {
			return PerspectiveMapWrapper.handlePerspective(this, transforms, cameraTransformType);
		}
	}

}
