package com.Sts.Types;

import com.Sts.DBTypes.*;

import javax.media.opengl.*;

/**
 * Created by IntelliJ IDEA.
 * User: Tom Lasseter
 * Date: Dec 9, 2008
 * Time: 10:05:55 AM
 * To change this template use File | Settings | File Templates.
 */
abstract public class StsSurfaceTexture extends StsTexture
{
    public StsSurface surface;

    public StsSurfaceTexture(StsSurface surface)
    {
        this.surface = surface;
    }

}

