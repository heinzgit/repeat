const API_BASE = 'http://localhost:8080/api';

const PAGE_SIZE = 100;
let currentPage = 1;
let filteredQuestions = [];
let selectedIds = new Set();

// 获取所有错题
async function fetchWrongQuestions() {
    try {
        const response = await fetch(`${API_BASE}/wrong-questions`);
        if (!response.ok) throw new Error('Failed to fetch');
        return await response.json();
    } catch (error) {
        console.error('Error:', error);
        alert('无法连接到服务器，请确保后端已启动');
        return [];
    }
}

// 获取重做记录
async function fetchRetryRecords(wrongQuestionId) {
    try {
        const response = await fetch(`${API_BASE}/wrong-questions/${wrongQuestionId}/retry-records`);
        return await response.json();
    } catch (error) {
        console.error('Error:', error);
        return [];
    }
}

// 添加错题
async function addWrongQuestion(data) {
    try {
        const response = await fetch(`${API_BASE}/wrong-questions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        return await response.json();
    } catch (error) {
        console.error('Error:', error);
        throw error;
    }
}

// 更新错题
async function updateWrongQuestion(id, data) {
    try {
        const response = await fetch(`${API_BASE}/wrong-questions/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        return await response.json();
    } catch (error) {
        console.error('Error:', error);
        throw error;
    }
}

// 删除错题
async function deleteWrongQuestion(id) {
    try {
        await fetch(`${API_BASE}/wrong-questions/${id}`, { method: 'DELETE' });
    } catch (error) {
        console.error('Error:', error);
        throw error;
    }
}

// 添加重做记录
async function addRetryRecord(data) {
    try {
        const response = await fetch(`${API_BASE}/retry-records`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        return await response.json();
    } catch (error) {
        console.error('Error:', error);
        throw error;
    }
}

// 批量添加重做记录
async function addBatchRetryRecords(data) {
    try {
        const response = await fetch(`${API_BASE}/retry-records/batch`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        if (!response.ok) throw new Error('Batch retry failed');
        return await response.json();
    } catch (error) {
        console.error('Error:', error);
        throw error;
    }
}

// 渲染错题列表
function renderWrongQuestions(questions) {
    const container = document.getElementById('wrongQuestionList');

    if (!questions || questions.length === 0) {
        container.innerHTML = '<tr><td colspan="9" class="empty-state">暂无错题记录</td></tr>';
        updateSelectAllState(questions);
        return;
    }

    container.innerHTML = questions.map(q => {
        const checked = selectedIds.has(q.id) ? 'checked' : '';
        const rowClass = selectedIds.has(q.id) ? 'selected-row' : '';
        return `
        <tr class="${rowClass}">
            <td class="checkbox-cell"><input type="checkbox" class="row-checkbox" data-id="${q.id}" ${checked} onchange="toggleRowSelection(${q.id}, this.checked)"></td>
            <td>${q.grade || '-'}</td>
            <td>${q.subject || '-'}</td>
            <td>${q.source || '-'}</td>
            <td>${q.questionNo || '-'}</td>
            <td>${q.category || '-'}</td>
            <td>${q.wrongDate || '-'}</td>
            <td class="status-cell"><span class="status-badge status-${q.status}">${q.status || '错误'}</span></td>
            <td>
                <button class="btn-small btn-edit" onclick="openEditModal(${q.id})">编辑</button>
                <button class="btn-small btn-retry" onclick="openRetryModal(${q.id})">重做</button>
                <button class="btn-small btn-delete" onclick="confirmDelete(${q.id})">删除</button>
            </td>
        </tr>
    `;
    }).join('');

    updateSelectAllState(questions);
}

// 切换行选中状态
function toggleRowSelection(id, checked) {
    if (checked) {
        selectedIds.add(id);
    } else {
        selectedIds.delete(id);
    }
    updateBulkButtonState();
    updateRowHighlight(id, checked);
}

// 更新行高亮
function updateRowHighlight(id, checked) {
    const checkbox = document.querySelector(`.row-checkbox[data-id="${id}"]`);
    if (checkbox) {
        const row = checkbox.closest('tr');
        if (row) row.classList.toggle('selected-row', checked);
    }
}

// 更新全选 checkbox 状态
function updateSelectAllState(currentPageQuestions) {
    const selectAll = document.getElementById('selectAll');
    if (!selectAll || !currentPageQuestions || currentPageQuestions.length === 0) {
        if (selectAll) {
            selectAll.checked = false;
            selectAll.indeterminate = false;
        }
        return;
    }
    const pageIds = currentPageQuestions.map(q => q.id);
    const selectedOnPage = pageIds.filter(id => selectedIds.has(id));
    selectAll.checked = selectedOnPage.length === pageIds.length;
    selectAll.indeterminate = selectedOnPage.length > 0 && selectedOnPage.length < pageIds.length;
}

// 全选/取消全选当前页
function toggleSelectAll(checkbox) {
    const checkboxes = document.querySelectorAll('.row-checkbox');
    checkboxes.forEach(cb => {
        const id = parseInt(cb.dataset.id);
        if (cb.checked !== checkbox.checked) {
            cb.checked = checkbox.checked;
            if (checkbox.checked) {
                selectedIds.add(id);
            } else {
                selectedIds.delete(id);
            }
            updateRowHighlight(id, checkbox.checked);
        }
    });
    updateBulkButtonState();
}

// 更新批量按钮状态
function updateBulkButtonState() {
    const btn = document.getElementById('bulkRetryBtn');
    const clearBtn = document.getElementById('clearSelectionBtn');
    const count = selectedIds.size;
    btn.textContent = `批量重做 (${count})`;
    btn.disabled = count === 0;
    clearBtn.disabled = count === 0;
}

// 清除所有选择
function clearSelection() {
    selectedIds.clear();
    document.querySelectorAll('.row-checkbox').forEach(cb => cb.checked = false);
    document.querySelectorAll('#wrongQuestionList tr').forEach(tr => tr.classList.remove('selected-row'));
    const selectAll = document.getElementById('selectAll');
    if (selectAll) {
        selectAll.checked = false;
        selectAll.indeterminate = false;
    }
    updateBulkButtonState();
}

// 渲染重做记录
async function renderRetryRecords(wrongQuestionId) {
    const container = document.getElementById('retryRecordsList');
    const records = await fetchRetryRecords(wrongQuestionId);

    if (!records || records.length === 0) {
        container.innerHTML = '<div class="empty-state">暂无重做记录</div>';
        return;
    }

    container.innerHTML = records.map(r => `
        <div class="retry-record">
            <div class="retry-record-info">
                <strong>重做日期:</strong> ${r.retryDate}
            </div>
            <div class="retry-record-result ${r.result === '通过' ? 'pass' : 'fail'}">
                ${r.result}
            </div>
        </div>
    `).join('');
}

// 筛选错题
async function filterQuestions() {
    const grade = document.getElementById('currentGrade').value;
    const subject = document.getElementById('filterSubject').value;
    const statusSelect = document.getElementById('filterStatus');
    const keyword = document.getElementById('filterKeyword').value.trim().toLowerCase();

    // Get selected statuses
    const selectedStatuses = Array.from(statusSelect.selectedOptions).map(opt => opt.value);

    let questions = await fetchWrongQuestions();

    if (grade) {
        questions = questions.filter(q => q.grade === grade);
    }
    if (subject) {
        questions = questions.filter(q => q.subject === subject);
    }
    if (selectedStatuses.length > 0) {
        questions = questions.filter(q => q.status && selectedStatuses.includes(q.status));
    }
    if (keyword) {
        questions = questions.filter(q => q.source && q.source.toLowerCase().includes(keyword));
    }

    // Save current grade to localStorage
    if (grade) {
        localStorage.setItem('currentGrade', grade);
    }

    filteredQuestions = questions;
    pruneSelection();
    currentPage = 1;
    renderCurrentPage();
}

// 清除筛选
function clearFilters() {
    document.getElementById('currentGrade').value = '';
    document.getElementById('filterSubject').value = '';
    document.getElementById('filterStatus').selectedIndex = -1;
    // Select "错误" by default
    const statusSelect = document.getElementById('filterStatus');
    for (let option of statusSelect.options) {
        if (option.value === '错误') {
            option.selected = true;
            break;
        }
    }
    document.getElementById('filterKeyword').value = '';
    localStorage.removeItem('currentGrade');
    selectedIds.clear();
    loadQuestions();
}

// 清除当前列表中已不存在的选择
function pruneSelection() {
    const validIds = new Set(filteredQuestions.map(q => q.id));
    for (const id of [...selectedIds]) {
        if (!validIds.has(id)) selectedIds.delete(id);
    }
}

// 确认删除
async function confirmDelete(id) {
    if (confirm('确定要删除这条错题记录吗？')) {
        await deleteWrongQuestion(id);
        loadQuestions();
    }
}

// 加载错题列表
async function loadQuestions() {
    const questions = await fetchWrongQuestions();
    filteredQuestions = questions;
    pruneSelection();
    currentPage = 1;
    renderCurrentPage();
}

// 渲染当前页
function renderCurrentPage() {
    const start = (currentPage - 1) * PAGE_SIZE;
    const pageItems = filteredQuestions.slice(start, start + PAGE_SIZE);
    renderWrongQuestions(pageItems);
    renderPagination();
}

// 渲染分页控件
function renderPagination() {
    const container = document.getElementById('pagination');
    const total = filteredQuestions.length;
    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

    if (total === 0) {
        container.innerHTML = '';
        return;
    }

    const start = (currentPage - 1) * PAGE_SIZE + 1;
    const end = Math.min(currentPage * PAGE_SIZE, total);

    const pageNumbers = buildPageNumbers(currentPage, totalPages);

    container.innerHTML = `
        <div class="pagination-info">第 ${start}-${end} 条,共 ${total} 条</div>
        <div class="pagination-controls">
            <button class="btn-page" ${currentPage === 1 ? 'disabled' : ''} onclick="goToPage(1)">首页</button>
            <button class="btn-page" ${currentPage === 1 ? 'disabled' : ''} onclick="goToPage(${currentPage - 1})">上一页</button>
            ${pageNumbers.map(p => {
                if (p === '...') {
                    return '<span class="page-ellipsis">...</span>';
                }
                const active = p === currentPage ? 'active' : '';
                return `<button class="btn-page ${active}" onclick="goToPage(${p})">${p}</button>`;
            }).join('')}
            <button class="btn-page" ${currentPage === totalPages ? 'disabled' : ''} onclick="goToPage(${currentPage + 1})">下一页</button>
            <button class="btn-page" ${currentPage === totalPages ? 'disabled' : ''} onclick="goToPage(${totalPages})">末页</button>
        </div>
    `;
}

// 构建页码序列,总页数过多时省略中间
function buildPageNumbers(current, total) {
    if (total <= 7) {
        return Array.from({ length: total }, (_, i) => i + 1);
    }
    const pages = new Set([1, total, current, current - 1, current + 1]);
    if (current <= 3) [2, 3, 4].forEach(p => pages.add(p));
    if (current >= total - 2) [total - 1, total - 2, total - 3].forEach(p => pages.add(p));
    const sorted = [...pages].filter(p => p >= 1 && p <= total).sort((a, b) => a - b);
    const result = [];
    for (let i = 0; i < sorted.length; i++) {
        if (i > 0 && sorted[i] - sorted[i - 1] > 1) result.push('...');
        result.push(sorted[i]);
    }
    return result;
}

// 跳转到指定页
function goToPage(page) {
    const totalPages = Math.max(1, Math.ceil(filteredQuestions.length / PAGE_SIZE));
    if (page < 1 || page > totalPages || page === currentPage) return;
    currentPage = page;
    renderCurrentPage();
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// 模态框控制
function openRetryModal(wrongQuestionId) {
    document.getElementById('retryWrongQuestionId').value = wrongQuestionId;
    document.getElementById('retryDate').value = new Date().toISOString().split('T')[0];
    renderRetryRecords(wrongQuestionId);
    document.getElementById('retryModal').style.display = 'block';
}

async function openEditModal(id) {
    const questions = await fetchWrongQuestions();
    const q = questions.find(question => question.id === id);

    if (!q) return;

    document.getElementById('editId').value = q.id;
    document.getElementById('editGrade').value = q.grade;
    document.getElementById('editSubject').value = q.subject;
    document.getElementById('editSource').value = q.source || '';
    document.getElementById('editQuestionNo').value = q.questionNo || '';
    document.getElementById('editCategory').value = q.category;
    document.getElementById('editWrongDate').value = q.wrongDate;
    document.getElementById('editStatus').value = q.status || '错误';

    document.getElementById('editModal').style.display = 'block';
}

function closeModals() {
    document.querySelectorAll('.modal').forEach(modal => {
        modal.style.display = 'none';
    });
}

// 事件监听
document.addEventListener('DOMContentLoaded', () => {
    // 设置默认日期
    document.getElementById('wrongDate').value = new Date().toISOString().split('T')[0];

    // 加载保存的年级并自动筛选
    const savedGrade = localStorage.getItem('currentGrade');
    if (savedGrade) {
        document.getElementById('currentGrade').value = savedGrade;
    }
    filterQuestions();

    // 年级切换时自动筛选
    document.getElementById('currentGrade').addEventListener('change', filterQuestions);

    // 打开添加错题模态框 - 加载上次保存的值
    document.getElementById('addQuestionBtn').addEventListener('click', () => {
        // 设置日期为今天
        document.getElementById('wrongDate').value = new Date().toISOString().split('T')[0];

        // 加载上次保存的值
        const lastGrade = localStorage.getItem('lastGrade');
        const lastSubject = localStorage.getItem('lastSubject');
        const lastSource = localStorage.getItem('lastSource');

        if (lastGrade) document.getElementById('grade').value = lastGrade;
        if (lastSubject) document.getElementById('subject').value = lastSubject;
        if (lastSource) document.getElementById('source').value = lastSource;

        // 默认类别为"做错"
        document.getElementById('category').value = '做错';

        document.getElementById('addModal').style.display = 'block';
    });

    // 添加错题表单提交
    document.getElementById('wrongQuestionForm').addEventListener('submit', async (e) => {
        e.preventDefault();

        const grade = document.getElementById('grade').value;
        const subject = document.getElementById('subject').value;
        const source = document.getElementById('source').value;

        const data = {
            grade: grade,
            subject: subject,
            source: source,
            questionNo: document.getElementById('questionNo').value,
            category: document.getElementById('category').value || '做错',
            wrongDate: document.getElementById('wrongDate').value,
            status: '错误'
        };

        try {
            await addWrongQuestion(data);

            // 保存本次填写的内容作为默认值
            localStorage.setItem('lastGrade', grade);
            localStorage.setItem('lastSubject', subject);
            localStorage.setItem('lastSource', source);

            document.getElementById('wrongQuestionForm').reset();
            document.getElementById('wrongDate').value = new Date().toISOString().split('T')[0];
            document.getElementById('addModal').style.display = 'none';
            loadQuestions();
            alert('添加成功！');
        } catch (error) {
            alert('添加失败，请重试');
        }
    });

    // 筛选按钮
    document.getElementById('filterBtn').addEventListener('click', filterQuestions);
    document.getElementById('clearFilterBtn').addEventListener('click', clearFilters);

    // CSV导入按钮
    document.getElementById('importBtn').addEventListener('click', async () => {
        const fileInput = document.getElementById('csvFile');
        const file = fileInput.files[0];

        if (!file) {
            alert('请选择CSV文件');
            return;
        }

        const formData = new FormData();
        formData.append('file', file);

        try {
            const response = await fetch(`${API_BASE}/wrong-questions/import`, {
                method: 'POST',
                body: formData
            });

            if (!response.ok) throw new Error('Import failed');

            const imported = await response.json();
            alert(`成功导入 ${imported.length} 条错题记录`);
            fileInput.value = '';
            loadQuestions();
        } catch (error) {
            console.error('Error:', error);
            alert('导入失败，请检查CSV格式');
        }
    });

    // 编辑错题表单提交
    document.getElementById('editQuestionForm').addEventListener('submit', async (e) => {
        e.preventDefault();

        const id = document.getElementById('editId').value;
        const data = {
            grade: document.getElementById('editGrade').value,
            subject: document.getElementById('editSubject').value,
            source: document.getElementById('editSource').value,
            questionNo: document.getElementById('editQuestionNo').value,
            category: document.getElementById('editCategory').value,
            wrongDate: document.getElementById('editWrongDate').value,
            status: document.getElementById('editStatus').value
        };

        try {
            await updateWrongQuestion(id, data);
            closeModals();
            loadQuestions();
            alert('更新成功！');
        } catch (error) {
            alert('更新失败，请重试');
        }
    });

    // 添加重做记录表单提交
    document.getElementById('retryRecordForm').addEventListener('submit', async (e) => {
        e.preventDefault();

        const wrongQuestionId = document.getElementById('retryWrongQuestionId').value;
        const data = {
            wrongQuestionId: parseInt(wrongQuestionId),
            retryDate: document.getElementById('retryDate').value,
            result: document.getElementById('retryResult').value
        };

        try {
            await addRetryRecord(data);
            document.getElementById('retryResult').value = '';
            renderRetryRecords(wrongQuestionId);
            loadQuestions();
            alert('添加成功！');
        } catch (error) {
            alert('添加失败，请重试');
        }
    });

    // 关闭模态框
    document.querySelectorAll('.close').forEach(btn => {
        btn.addEventListener('click', closeModals);
    });

    window.addEventListener('click', (e) => {
        if (e.target.classList.contains('modal')) {
            closeModals();
        }
    });

    // 全选 checkbox
    document.getElementById('selectAll').addEventListener('change', (e) => {
        toggleSelectAll(e.target);
    });

    // 批量重做按钮
    document.getElementById('bulkRetryBtn').addEventListener('click', openBatchRetryModal);

    // 清除选择按钮
    document.getElementById('clearSelectionBtn').addEventListener('click', clearSelection);

    // 批量重做表单提交
    document.getElementById('batchRetryForm').addEventListener('submit', async (e) => {
        e.preventDefault();

        const data = {
            wrongQuestionIds: [...selectedIds],
            retryDate: document.getElementById('batchRetryDate').value,
            result: document.getElementById('batchRetryResult').value
        };

        try {
            const saved = await addBatchRetryRecords(data);
            closeModals();
            clearSelection();
            document.getElementById('batchRetryResult').value = '';
            loadQuestions();
            alert(`成功添加 ${saved.length} 条重做记录`);
        } catch (error) {
            alert('批量添加失败，请重试');
        }
    });
});

// 打开批量重做模态框
function openBatchRetryModal() {
    if (selectedIds.size === 0) return;
    document.getElementById('batchRetryCount').textContent = `已选中 ${selectedIds.size} 道错题`;
    document.getElementById('batchRetryDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('batchRetryResult').value = '';
    document.getElementById('batchRetryModal').style.display = 'block';
}
