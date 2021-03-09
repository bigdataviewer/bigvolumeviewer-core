/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2021 Tobias Pietzsch
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
package tpietzsch.shadergen;

import tpietzsch.backend.GpuContext;

public interface Shader
{
	Uniform1i getUniform1i( final String key );

	Uniform2i getUniform2i( final String key );

	Uniform3i getUniform3i( final String key );

	Uniform4i getUniform4i( final String key );

	Uniform1iv getUniform1iv( final String key );

	Uniform2iv getUniform2iv( final String key );

	Uniform3iv getUniform3iv( final String key );

	Uniform4iv getUniform4iv( final String key );

	Uniform1f getUniform1f( final String key );

	Uniform2f getUniform2f( final String key );

	Uniform3f getUniform3f( final String key );

	Uniform4f getUniform4f( final String key );

	Uniform1fv getUniform1fv( final String key );

	Uniform2fv getUniform2fv( final String key );

	Uniform3fv getUniform3fv( final String key );

	Uniform4fv getUniform4fv( final String key );

	UniformMatrix3f getUniformMatrix3f( final String key );

	UniformMatrix4f getUniformMatrix4f( final String key );

	UniformSampler getUniformSampler( final String key );

	void use( final GpuContext gpu );

	void bindSamplers( final GpuContext gpu );

	void setUniforms( final GpuContext gpu );

	StringBuilder getVertexShaderCode();

	StringBuilder getFragmentShaderCode();
}
