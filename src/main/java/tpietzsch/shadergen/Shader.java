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
