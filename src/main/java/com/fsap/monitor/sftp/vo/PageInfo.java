package com.fsap.monitor.sftp.vo;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageInfo implements Serializable {

	private static final long serialVersionUID = 1L;

	@Default
	private int pageIndex = 1;
	@Default
	private int pageSize = 10;
	private int totalRows;
	private int totalPages;
	private String sortColumn;
	private String sortType;

	public static PageInfo newInstance(int pageIndex, int pageSize, int totalRows) {
		PageInfo pageInfo = new PageInfo();
		int size = pageSize;
		int index = pageIndex;
		int rows = totalRows;
		pageInfo.setPageSize(size);
		pageInfo.setTotalRows(totalRows);
		int pages = rows % pageSize != 0 ? rows / size + 1 : rows / size;
		pageInfo.setTotalPages(pages);
		pageInfo.setPageIndex(index > pages ? pages : index);
		return pageInfo;
	}
}
