package csdemo;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opencl.CLCommandQueue;
import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLEvent;
import com.jogamp.opencl.CLEventList;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLPlatform;
import com.jogamp.opencl.CLProgram;
import com.jogamp.opencl.gl.CLGLContext;
import com.jogamp.opencl.util.CLPlatformFilters;
import java.io.IOException;
import static com.jogamp.opencl.CLMemory.Mem.*;
import com.jogamp.opencl.gl.CLGLBuffer;
import com.jogamp.opengl.GL4;
import static com.jogamp.opengl.GL4.*;
import java.nio.IntBuffer;

/**
 *
 * @author xjurc
 */
public class CLPerformance {
    
    private CLDevice device;
    private CLCommandQueue queue;
    
    private CLProgram addProgram;
    private CLProgram arrayProgram;
    
    private CLGLBuffer<?> bufferA;
    private CLGLBuffer<?> bufferB;
    private CLGLBuffer<?> bufferC;
    
    private int aBuffer;
    private int bBuffer;
    private int resultBuffer;
    
    private static final int ELEMENT_COUNT = 512 * 256;
    
    private boolean writePerformanceInfo = false;
    
    public void init(GL4 gl) {
        CLPlatform platform = CLPlatform.getDefault(CLPlatformFilters.glSharing(gl.getContext()));
        System.out.println("OpenCL platform: " + platform);
        
        device = platform.getMaxFlopsDevice(CLDevice.Type.GPU);
        CLGLContext cl = CLGLContext.create(gl.getContext(), device);
        System.out.println("OpenCL device: " + device);
        
        try {
            addProgram = cl.createProgram(CLPerformance.class.getResourceAsStream("/resources/cl/add.cl")).build();
            arrayProgram = cl.createProgram(CLPerformance.class.getResourceAsStream("/resources/cl/array.cl")).build();
        } catch (IOException ex) {
            System.err.println("Resource loading failed. " + ex.getMessage());
            System.exit(1);
        }
        
        int buffers[] = new int[3];
        gl.glGenBuffers(3, buffers, 0);
        aBuffer = buffers[0];
        bBuffer = buffers[1];
        resultBuffer = buffers[2];
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, aBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, 512 * 256 * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32I, GL_RED, GL_INT, IntBuffer.wrap(new int [] { 2 }));
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, bBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, 512 * 256 * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32I, GL_RED, GL_INT, IntBuffer.wrap(new int [] { 3 }));
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, resultBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, 512 * 256 * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        // create command queue on device.
        queue = device.createCommandQueue(CLCommandQueue.Mode.PROFILING_MODE);

        // A, B are input buffers, C is for the result
        bufferA = cl.createFromGLBuffer(aBuffer, 512 * 256 * Buffers.SIZEOF_INT, READ_ONLY);
        bufferB = cl.createFromGLBuffer(bBuffer, 512 * 256 * Buffers.SIZEOF_INT, READ_ONLY);
        bufferC = cl.createFromGLBuffer(resultBuffer, 512 * 256 * Buffers.SIZEOF_INT, WRITE_ONLY);
        
        /*System.out.println("used device memory: "
            + (bufferA.getCLSize() + bufferB.getCLSize() + bufferC.getCLSize()) / 1024 / 1024 + " MB");*/
    }
    
    public void run(GL4 gl, int threadCount) {
        // get a reference to the kernel function with the name 'VectorAdd'
        // and map the buffers to its input parameters.
        CLKernel arrayKernel = arrayProgram.createCLKernel("array");
        arrayKernel.putArg(bufferC).putArg(threadCount).putArg(512);

        int localWorkSize = Math.min(device.getMaxWorkGroupSize(), 256);  // Local work size dimensions
        int globalWorkSize = roundUp(localWorkSize, threadCount);   // rounded up to the nearest multiple of the localWorkSize
        
        CLEventList events = new CLEventList(2);
        
        gl.glFinish();
        
        queue.putAcquireGLObject(bufferC)
             .put1DRangeKernel(arrayKernel, 0, globalWorkSize, localWorkSize, events)
             .putReleaseGLObject(bufferC)
             .finish();
        
        long start1 = events.getEvent(0).getProfilingInfo(CLEvent.ProfilingCommand.START);
        long end1 = events.getEvent(0).getProfilingInfo(CLEvent.ProfilingCommand.END);
        long time1 = end1 - start1;
        
        CLKernel addKernel = addProgram.createCLKernel("add");
        addKernel.putArgs(bufferA, bufferB, bufferC).putArg(ELEMENT_COUNT);
        
        globalWorkSize = roundUp(localWorkSize, ELEMENT_COUNT);
        
        // asynchronous write of data to GPU device,
        // followed by blocking read to get the computed results back.
        queue.putAcquireGLObject(bufferA)
             .putAcquireGLObject(bufferB)
             .putAcquireGLObject(bufferC)
             .put1DRangeKernel(addKernel, 0, globalWorkSize, localWorkSize, events)
             .putReleaseGLObject(bufferA)
             .putReleaseGLObject(bufferB)
             .putReleaseGLObject(bufferC)
             .finish();
        
        long start2 = events.getEvent(1).getProfilingInfo(CLEvent.ProfilingCommand.START);
        long end2 = events.getEvent(1).getProfilingInfo(CLEvent.ProfilingCommand.END);
        long time2 = end2 - start2;
        
        if (writePerformanceInfo) {
            writePerformanceInfo = false;
            System.out.println("Time elapsed (OpenCL: array): " + (time1 / 1000000.0) + " ms");
            System.out.println("Time elapsed (OpenCL: add): " + (time2 / 1000000.0) + " ms");
            IntBuffer result = Buffers.newDirectIntBuffer(10);
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, resultBuffer);
            gl.glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, 10 * Buffers.SIZEOF_INT, result);
            for (int i = 0; i < 10; i++) {
                System.out.print(result.get(i) + " ");
            }
            System.out.println();
        }
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
