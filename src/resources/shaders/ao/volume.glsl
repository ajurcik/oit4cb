#version 430 core

const float invPackRatio = 1.0 / 0.74;

uniform uint atomCount;
uniform float voxelSize;

layout(std430) buffer Atoms { 
    vec4 atoms[];
};

layout(std430) buffer AtomsVolume { 
    float atomsVolume[];
};

uniform layout(r32ui) uimage3D volumeImg;

layout(local_size_x = 256) in;

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= atomCount) {
        return;
    }

    vec4 atom = atoms[index];
    float atomVol = atomsVolume[index];
    ivec3 volumeIdx = ivec3(floor(atom.xyz / voxelSize));

    float invVoxelSize3 = 1.0 / (voxelSize * voxelSize * voxelSize);
    uint old = imageLoad(volumeImg, volumeIdx).r;
    uint assumed;
    do {
        assumed = old;
        float volume = uintBitsToFloat(assumed) + atomVol * invVoxelSize3; // * invPackRatio;
        old = imageAtomicCompSwap(volumeImg, volumeIdx, assumed, floatBitsToUint(volume));
    } while (assumed != old);
}