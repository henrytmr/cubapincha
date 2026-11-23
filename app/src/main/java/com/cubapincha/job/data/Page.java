package com.cubapincha.job.data;

import java.util.ArrayList;
import java.util.List;

/** Página de resultados con cursor. */
public class Page<T> {
    public List<T> items = new ArrayList<>();
    public String nextCursor;
    public int total;
}
