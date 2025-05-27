/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2025 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bvv.core.backend;

import bvv.core.shadergen.Shader;
import java.nio.Buffer;

public interface GpuContext
{
	// build and cache and bind
	void use( Shader shader );

	// from cached shader thingie and gl context, build SetUniforms
	SetUniforms getUniformSetter( Shader shader );

	/**
	 * @param stagingBuffer staging buffer to bind
	 * @return id of previously bound staging buffer
	 */
	int bindStagingBuffer( StagingBuffer stagingBuffer );

	/**
	 * @param id staging buffer id to bind
	 * @return id of previously bound staging buffer
	 */
	int bindStagingBufferId( int id );

	/**
	 * @param texture texture to bind
	 * @return id of previously bound texture
	 */
	int bindTexture( Texture texture );

	/**
	 * @param texture texture to bind
	 * @param unit texture unit to bind to
	 */
	void bindTexture( Texture texture, int unit );

	/**
	 * @param id texture id to bind
	 * @param numTexDimensions texture target: 1, 2, or 3
	 * @return id of previously bound texture
	 */
	int bindTextureId( int id, int numTexDimensions );

	// map staging buffer, initialize and cache if necessary
	// previous staging buffer binding is restored when done
	Buffer map( StagingBuffer stagingBuffer );

	// unmap a (mapped) staging buffer
	// previous staging buffer binding is restored when done
	void unmap( StagingBuffer stagingBuffer );

	// delete a texture (if it has already been allocated)
	// used to redefine a texture that has changed size
	void delete( Texture texture );

	// upload texture block from staging buffer
	// previous staging buffer binding is restored when done
	void texSubImage3D( StagingBuffer stagingBuffer, Texture3D texture, int xoffset, int yoffset, int zoffset, int width, int height, int depth, long pixels_buffer_offset );

	// upload texture block from Buffer
	void texSubImage3D( Texture3D texture, int xoffset, int yoffset, int zoffset, int width, int height, int depth, Buffer pixels );
}
