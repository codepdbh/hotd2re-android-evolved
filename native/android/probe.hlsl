struct VertexOut {
    float4 position : SV_Position;
    float2 uv : TEXCOORD0;
};

VertexOut vs_main(uint id : SV_VertexID) {
    // One triangle covers the viewport. The GPU clips its excess at the edges.
    const float2 uv = float2((id << 1) & 2, id & 2);
    VertexOut output;
    output.position = float4(uv * 2.0 - 1.0, 0.0, 1.0);
    output.uv = uv;
    return output;
}

Texture2D probeImage : register(t0, space2);
SamplerState probeSampler : register(s0, space2);
cbuffer ProbeState : register(b0, space3) {
    float4 tint;
};

float4 ps_main(VertexOut input) : SV_Target0 {
    return probeImage.Sample(probeSampler, input.uv) * tint;
}
