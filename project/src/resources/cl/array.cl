kernel void array(global float * result, int elementCount, int operationCount) {
    // get index into global data array
    int idx = get_global_id(0);

    // bound check, equivalent to the limit on a 'for' loop
    if (idx >= elementCount)  {
        return;
    }

    int offset = 0;
    //int offset = gl_LocalInvocationID.x * 380;
    float array[768];
    for (int i = 0; i < operationCount; i++) {
        array[offset + i] = 1.0;
    }
    
    float sum;
    for (int i = 0; i < operationCount; i++) {
        sum += array[offset + i];
    }

    *result = sum;
}