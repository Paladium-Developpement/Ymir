package fr.paladium.ymir.internal.client.network;

import cpw.mods.fml.relauncher.ReflectionHelper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.network.ForgeMessage.DimensionRegisterMessage;

public class YmirDimensionMessageHandler extends SimpleChannelInboundHandler<DimensionRegisterMessage> {

	@Override
	protected void channelRead0(final ChannelHandlerContext context, final DimensionRegisterMessage message) {
		final int dimensionId = ReflectionHelper.getPrivateValue(DimensionRegisterMessage.class, message, "dimensionId");
		final int providerId = ReflectionHelper.getPrivateValue(DimensionRegisterMessage.class, message, "providerId");
		if (DimensionManager.isDimensionRegistered(dimensionId) && DimensionManager.getProviderType(dimensionId) != providerId) {
			DimensionManager.unregisterDimension(dimensionId);
		}
		context.fireChannelRead(message);
	}

}