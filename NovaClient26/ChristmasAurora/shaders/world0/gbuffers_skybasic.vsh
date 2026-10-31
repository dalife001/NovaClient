#version 120
varying vec4 glcolor;
varying float starFlag;
void main(){
    gl_Position = ftransform();
    glcolor = gl_Color;
    starFlag = float(gl_Color.r == gl_Color.g && gl_Color.g == gl_Color.b && gl_Color.r > 0.0);
}
