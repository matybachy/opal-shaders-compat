#version 120
uniform sampler2D colortex1;
varying vec2 texcoord;
void main() {
/* DRAWBUFFERS:2 */
	gl_FragData[0] = vec4(texture2D(colortex1, texcoord).rgb, 1.0);
}
