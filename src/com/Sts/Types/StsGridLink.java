package com.Sts.Types;

import com.Sts.DBTypes.*;
import com.Sts.Utilities.*;

/**
 * � tom 9/28/2015
 * All Rights Reserved
 * No part of this website or any of its contents may be reproduced, copied, modified or adapted, without the prior written consent of the author, unless otherwise indicated for stand-alone materials.
 */
public class StsGridLink implements Cloneable
{
	public byte linkType = LINK_NONE;
	public float z;
	public float zNext;
	public StsPatchGrid grid;
	public StsPatchGrid gridNext;
	public float correlation;

	static final public byte LINK_NONE = -1;
	static final public byte LINK_RIGHT = 0;
	static final public byte LINK_UP = 1;
	static final public byte LINK_LEFT = 2;
	static final public byte LINK_DOWN = 3;

	static float nullValue = StsParameters.nullValue;

	public StsGridLink(int dir, float z, float zNext, StsPatchGrid grid, StsPatchGrid gridNext, float correlation)
	{
		this.linkType = (byte)dir;
		this.z = z;
		this.zNext = zNext;
		this.grid = grid;
		this.gridNext = gridNext;
		this.correlation = correlation;
	}

	public StsGridLink(StsGridLink gridLink)
	{
		linkType = gridLink.linkType;
		z = gridLink.z;
		zNext = gridLink.zNext;
		grid = gridLink.grid;
		gridNext = gridLink.gridNext;
		correlation = gridLink.correlation;
	}

	public boolean isSameGrid()
	{
		return grid == gridNext;
	}

	static public float getZ(StsGridLink link)
	{
		if(link == null) return nullValue;
		return link.z;
	}

	static public float getNextZ(StsGridLink link)
	{
		if(link == null) return nullValue;
		return link.zNext;
	}
}