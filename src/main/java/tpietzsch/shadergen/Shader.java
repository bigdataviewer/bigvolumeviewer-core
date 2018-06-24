package tpietzsch.shadergen;

import tpietzsch.backend.GpuContext;

public interface Shader
{
	Uniform1i getUniform1i( final String key );

	Uniform2i getUniform2i( final String key );

	Uniform3i getUniform3i( final String key );

	Uniform4i getUniform4i( final String key );

	Uniform1f getUniform1f( final String key );

	Uniform2f getUniform2f( final String key );

	Uniform3f getUniform3f( final String key );

	Uniform4f getUniform4f( final String key );

	Uniform3fv getUniform3fv( final String key );

	UniformMatrix4f getUniformMatrix4f( final String key );

	void use( final GpuContext gpu );

	void setUniforms( final GpuContext gpu );

	StringBuilder getVertexShaderCode();

	StringBuilder getFragementShaderCode();
}
