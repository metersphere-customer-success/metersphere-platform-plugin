package io.metersphere.plugin.tapd.impl;

import io.metersphere.plugin.platform.spi.AbstractPlatformPlugin;

public class TapdPlugin extends AbstractPlatformPlugin {

	public static final String TAPD_PLUGIN_NAME = "TAPD";
	private static final String LOGO_PATH = "static/tapd.png";
	private static final String DESCRIPTION = "敏捷协作、创造新的可能";

	@Override
	public String getDescription() {
		return DESCRIPTION;
	}

	@Override
	public String getLogo() {
		return LOGO_PATH;
	}

	@Override
	public boolean isXpack() {
		return false;
	}

	@Override
	public String getName() {
		return TAPD_PLUGIN_NAME;
	}
}
