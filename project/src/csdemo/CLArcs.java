package csdemo;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLCommandQueue;
import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLEvent;
import com.jogamp.opencl.CLEventList;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLMemory;
import com.jogamp.opencl.CLPlatform;
import com.jogamp.opencl.CLProgram;
import com.jogamp.opencl.gl.CLGLBuffer;
import com.jogamp.opencl.gl.CLGLContext;
import com.jogamp.opencl.util.CLPlatformFilters;
import com.jogamp.opengl.GL4;
import static com.jogamp.opengl.GL4.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 *
 * @author xjurc
 */
public class CLArcs {
    
    private CLDevice device;
    private CLCommandQueue queue;
    
    private CLKernel arcsKernel;
    
    private CLGLBuffer<?> atomsBuffer;
    private CLGLBuffer<?> neighborsBuffer;
    private CLGLBuffer<?> neighborsCountBuffer;
    private CLGLBuffer<?> smallCirclesBuffer;
    private CLGLBuffer<?> arcsBuffer;
    private CLGLBuffer<?> arcsCountBuffer;
    private CLGLBuffer<?> arcsHashesBuffer;
    private CLGLBuffer<?> smallCirclesVisibleBuffer;
    private CLBuffer<IntBuffer> countersBuffer;
    private CLBuffer<ByteBuffer> paramsBuffer;
    
    private boolean writePerformanceInfo = false;
    
    public void init(GL4 gl, int atomsGLBuffer, int neihborsGLBuffer, int neighborsCountGLBuffer, int smallCirclesGLBuffer,
            int arcsGLBuffer, int arcsCountGLBuffer, int arcsHashesGLBuffer, int smallCirclesVisibleGLBuffer) {
        CLPlatform platform = CLPlatform.getDefault(CLPlatformFilters.glSharing(gl.getContext()));
        System.out.println("OpenCL platform: " + platform);
        
        device = platform.getMaxFlopsDevice(CLDevice.Type.GPU);
        CLGLContext cl = CLGLContext.create(gl.getContext(), device);
        System.out.println("OpenCL device: " + device);
        
        try {
            CLProgram arcsProgram = cl.createProgram(CLPerformance.class.getResourceAsStream("/resources/cl/arcs.cl")).build();
            arcsKernel = arcsProgram.createCLKernel("arcs");
        } catch (IOException ex) {
            System.err.println("Resource loading failed. " + ex.getMessage());
            System.exit(1);
        }
        
        // create command queue on device.
        queue = device.createCommandQueue(CLCommandQueue.Mode.PROFILING_MODE);
        
        // create CL buffers
        atomsBuffer = createFromGLBuffer(cl, gl, atomsGLBuffer, CLMemory.Mem.READ_ONLY);
        neighborsBuffer = createFromGLBuffer(cl, gl, neihborsGLBuffer, CLMemory.Mem.READ_ONLY);
        neighborsCountBuffer = createFromGLBuffer(cl, gl, neighborsCountGLBuffer, CLMemory.Mem.READ_ONLY);
        smallCirclesBuffer = createFromGLBuffer(cl, gl, smallCirclesGLBuffer, CLMemory.Mem.READ_ONLY);
        arcsBuffer = createFromGLBuffer(cl, gl, arcsGLBuffer, CLMemory.Mem.WRITE_ONLY);
        arcsCountBuffer = createFromGLBuffer(cl, gl, arcsCountGLBuffer, CLMemory.Mem.WRITE_ONLY);
        arcsHashesBuffer = createFromGLBuffer(cl, gl, arcsHashesGLBuffer, CLMemory.Mem.WRITE_ONLY);
        smallCirclesVisibleBuffer = createFromGLBuffer(cl, gl, smallCirclesVisibleGLBuffer, CLMemory.Mem.WRITE_ONLY);
        countersBuffer = cl.createIntBuffer(4 * Buffers.SIZEOF_INT, CLMemory.Mem.READ_WRITE);
        paramsBuffer = cl.createByteBuffer(8 * Buffers.SIZEOF_INT, CLMemory.Mem.READ_ONLY);
        
        arcsKernel.putArgs(atomsBuffer, neighborsBuffer, neighborsCountBuffer, smallCirclesBuffer)
                .putArgs(arcsBuffer, arcsCountBuffer, arcsHashesBuffer, smallCirclesVisibleBuffer)
                .putArg(countersBuffer)
                .putArg(paramsBuffer);
    }
    
    public int computeArcs(GL4 gl, int atomCount, int maxNumNeighbors, int maxNumTotalArcHashes,
            int maxHashIterations, float probeRadius) {
        int localWorkSizeX = 64;
        int localWorkSizeY = 4;
        int globalWorkSizeX = roundUp(localWorkSizeX, maxNumNeighbors); // rounded up to the nearest multiple of the localWorkSize
        int globalWorkSizeY = roundUp(localWorkSizeY, atomCount);
        
        CLEventList events = new CLEventList(1);
        
        countersBuffer.getBuffer().put(0, 0);
        countersBuffer.getBuffer().put(1, 0);
        countersBuffer.getBuffer().put(2, 0);
        countersBuffer.getBuffer().put(3, 0);
        
        paramsBuffer.getBuffer().putInt(0, atomCount);
        paramsBuffer.getBuffer().putInt(4, maxNumNeighbors);
        paramsBuffer.getBuffer().putInt(8, maxNumTotalArcHashes);
        paramsBuffer.getBuffer().putInt(12, maxHashIterations);
        paramsBuffer.getBuffer().putFloat(16, probeRadius);
        
        gl.glFinish();
        
        queue.putAcquireGLObject(atomsBuffer)
                .putAcquireGLObject(neighborsBuffer)
                .putAcquireGLObject(neighborsCountBuffer)
                .putAcquireGLObject(smallCirclesBuffer)
                .putAcquireGLObject(arcsBuffer)
                .putAcquireGLObject(arcsCountBuffer)
                .putAcquireGLObject(arcsHashesBuffer)
                .putAcquireGLObject(smallCirclesVisibleBuffer)
                .putWriteBuffer(countersBuffer, false)
                .putWriteBuffer(paramsBuffer, false)
                .put2DRangeKernel(arcsKernel, 0, 0, globalWorkSizeX, globalWorkSizeY, localWorkSizeX, localWorkSizeY, events)
                .putReleaseGLObject(atomsBuffer)
                .putReleaseGLObject(neighborsBuffer)
                .putReleaseGLObject(neighborsCountBuffer)
                .putReleaseGLObject(smallCirclesBuffer)
                .putReleaseGLObject(arcsBuffer)
                .putReleaseGLObject(arcsCountBuffer)
                .putReleaseGLObject(arcsHashesBuffer)
                .putReleaseGLObject(smallCirclesVisibleBuffer)
                .putReadBuffer(countersBuffer, true)
                .finish();
        
        long start = events.getEvent(0).getProfilingInfo(CLEvent.ProfilingCommand.START);
        long end = events.getEvent(0).getProfilingInfo(CLEvent.ProfilingCommand.END);
        long time = end - start;
        
        if (writePerformanceInfo) {
            writePerformanceInfo = false;
            System.out.println("Time elapsed (OpenCL: arcs): " + (time / 1000000.0) + " ms");
        }
        
        return countersBuffer.getBuffer().get(3);
    }
    
    public int getHashErrorCount() {
        return countersBuffer.getBuffer().get(2);
    }
    
    private static CLGLBuffer<?> createFromGLBuffer(CLGLContext cl, GL4 gl, int glBuffer, CLMemory.Mem ... mems) {
        int[] size = new int[1];
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, glBuffer);
        gl.glGetBufferParameteriv(GL_SHADER_STORAGE_BUFFER, GL_BUFFER_SIZE, size, 0);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        return cl.createFromGLBuffer(glBuffer, size[0], mems);
    }
    
    private static int roundUp(int groupSize, int globalSize) {
        int r = globalSize % groupSize;
        if (r == 0) {
            return globalSize;
        } else {
            return globalSize + groupSize - r;
        }
    }
    
    public void writePerformanceInfo() {
        writePerformanceInfo = true;
    }
    
}
