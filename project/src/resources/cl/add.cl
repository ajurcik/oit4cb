kernel void add(global const int * a, global const int * b, global int * c, int elementCount) {
    // get index into global data array
    int idx = get_global_id(0);

    // bound check, equivalent to the limit on a 'for' loop
    if (idx >= elementCount)  {
        return;
    }

    // add the vector elements
    c[idx] = a[idx] + b[idx];
}