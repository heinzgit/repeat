const API_BASE = 'http://localhost:8080/api';

const PAGE_SIZE = 100;
let currentPage = 1;
let filteredQuestions = [];
let selectedIds = new Set();
let pdfExportInFlight = false;

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

// 生成并下载试卷 PDF
async function exportSelectedPdf() {
    if (pdfExportInFlight) return;

    const orderedIds = filteredQuestions
        .filter(question => selectedIds.has(question.id))
        .map(question => question.id);
    if (orderedIds.length === 0) return;

    const button = document.getElementById('bulkExportPdfBtn');
    const includeAnswers =
        document.getElementById('includeAnswersCheckbox').checked;
    pdfExportInFlight = true;
    button.disabled = true;
    button.textContent = '生成中...';

    try {
        const response = await fetch(`${API_BASE}/wrong-questions/export-pdf`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                wrongQuestionIds: orderedIds,
                includeAnswers: includeAnswers
            })
        });
        if (!response.ok) {
            let message = 'PDF 生成失败，请重试';
            try {
                const error = await response.json();
                if (error.message) message = error.message;
            } catch (_) {
                // 使用默认错误提示
            }
            throw new Error(message);
        }

        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `错题试卷_${new Date().toISOString().slice(0, 10)}.pdf`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    } catch (error) {
        console.error(error);
        alert(error.message || 'PDF 生成失败，请重试');
    } finally {
        pdfExportInFlight = false;
        updateBulkButtonState();
    }
}

// 获取错题的文件列表
async function fetchQuestionFiles(wrongQuestionId) {
    try {
        const response = await fetch(`${API_BASE}/wrong-questions/${wrongQuestionId}/files`);
        if (!response.ok) throw new Error('Failed to fetch files');
        return await response.json();
    } catch (error) {
        console.error('Error:', error);
        return [];
    }
}

// 上传文件
async function uploadQuestionFile(wrongQuestionId, file, type) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', type);
    const response = await fetch(`${API_BASE}/wrong-questions/${wrongQuestionId}/files`, {
        method: 'POST',
        body: formData
    });
    if (!response.ok) {
        const err = await response.text();
        throw new Error(err || 'Upload failed');
    }
    return await response.json();
}

// 删除文件
async function deleteQuestionFile(fileId) {
    const response = await fetch(`${API_BASE}/files/${fileId}`, {
        method: 'DELETE'
    });
    if (!response.ok) throw new Error('Delete failed');
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
    const exportBtn = document.getElementById('bulkExportPdfBtn');
    const clearBtn = document.getElementById('clearSelectionBtn');
    const count = selectedIds.size;
    btn.textContent = `批量重做 (${count})`;
    btn.disabled = count === 0;
    exportBtn.textContent = pdfExportInFlight ? '生成中...' : `生成试卷 PDF (${count})`;
    exportBtn.disabled = pdfExportInFlight || count === 0;
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
    const selectedStatuses = Array.from(
        document.querySelectorAll('#filterStatus input[type="checkbox"]:checked')
    ).map(checkbox => checkbox.value);
    const keyword = document.getElementById('filterKeyword').value.trim().toLowerCase();
    const startDate = document.getElementById('filterDateStart').value;
    const endDate = document.getElementById('filterDateEnd').value;

    if (startDate && endDate && startDate > endDate) {
        alert('开始日期不能晚于结束日期');
        return;
    }

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
    if (startDate && endDate) {
        questions = questions.filter(q => q.wrongDate && q.wrongDate >= startDate && q.wrongDate <= endDate);
    } else if (startDate) {
        questions = questions.filter(q => q.wrongDate === startDate);
    } else if (endDate) {
        questions = questions.filter(q => q.wrongDate === endDate);
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
    document.querySelectorAll('#filterStatus input[type="checkbox"]')
        .forEach(checkbox => checkbox.checked = false);
    document.getElementById('filterKeyword').value = '';
    document.getElementById('filterDateStart').value = '';
    document.getElementById('filterDateEnd').value = '';
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
        filterQuestions();
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
    document.getElementById('editAnswerText').value = q.answerText || '';

    // 重置待上传文件,加载已有附件
    resetPendingEditFiles();
    const files = await fetchQuestionFiles(id);
    existingEditFiles.question = files.filter(f => f.fileType === 'QUESTION');
    existingEditFiles.answer = files.filter(f => f.fileType === 'ANSWER');
    setEditActivePasteSection('question');
    renderEditFilePreviews();

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

        // 重置待上传文件状态,默认激活"题目图片"区
        resetPendingAddFiles();
        setActivePasteSection('question');

        document.getElementById('addModal').style.display = 'block';
    });

    // 添加错题表单提交(支持图片上传)
    document.getElementById('wrongQuestionForm').addEventListener('submit', async (e) => {
        e.preventDefault();

        const grade = document.getElementById('grade').value;
        const subject = document.getElementById('subject').value;
        const source = document.getElementById('source').value;

        const formData = new FormData();
        formData.append('grade', grade);
        formData.append('subject', subject);
        if (source) formData.append('source', source);
        formData.append('questionNo', document.getElementById('questionNo').value);
        formData.append('category', document.getElementById('category').value || '做错');
        formData.append('wrongDate', document.getElementById('wrongDate').value);
        formData.append('status', '错误');
        const answerText = document.getElementById('addAnswerText').value;
        if (answerText) formData.append('answerText', answerText);

        pendingAddFiles.question.forEach(f => formData.append('questionFiles', f));
        pendingAddFiles.answer.forEach(f => formData.append('answerFiles', f));

        try {
            const response = await fetch(`${API_BASE}/wrong-questions`, {
                method: 'POST',
                body: formData
            });
            if (!response.ok) throw new Error('Add failed');
            await response.json();

            // 保存本次填写的内容作为默认值
            localStorage.setItem('lastGrade', grade);
            localStorage.setItem('lastSubject', subject);
            localStorage.setItem('lastSource', source);

            document.getElementById('wrongQuestionForm').reset();
            document.getElementById('wrongDate').value = new Date().toISOString().split('T')[0];
            document.getElementById('addAnswerText').value = '';
            resetPendingAddFiles();
            document.getElementById('addModal').style.display = 'none';
            filterQuestions();
            alert('添加成功！');
        } catch (error) {
            console.error(error);
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
            filterQuestions();
        } catch (error) {
            console.error('Error:', error);
            alert('导入失败，请检查CSV格式');
        }
    });

    // 编辑错题表单提交(支持图片上传)
    document.getElementById('editQuestionForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        if (e.submitter?.id !== 'saveEditQuestionBtn') return;

        const id = document.getElementById('editId').value;
        const data = {
            grade: document.getElementById('editGrade').value,
            subject: document.getElementById('editSubject').value,
            source: document.getElementById('editSource').value,
            questionNo: document.getElementById('editQuestionNo').value,
            category: document.getElementById('editCategory').value,
            wrongDate: document.getElementById('editWrongDate').value,
            status: document.getElementById('editStatus').value,
            answerText: document.getElementById('editAnswerText').value
        };

        try {
            await updateWrongQuestion(id, data);

            // 上传待添加的图片
            for (const file of pendingEditFiles.question) {
                await uploadQuestionFile(id, file, 'question');
            }
            for (const file of pendingEditFiles.answer) {
                await uploadQuestionFile(id, file, 'answer');
            }

            resetPendingEditFiles();
            closeModals();
            filterQuestions();
            alert('更新成功！');
        } catch (error) {
            console.error(error);
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
            filterQuestions();
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

    // 生成试卷 PDF
    document.getElementById('bulkExportPdfBtn').addEventListener('click', exportSelectedPdf);

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
            filterQuestions();
            alert(`成功添加 ${saved.length} 条重做记录`);
        } catch (error) {
            alert('批量添加失败，请重试');
        }
    });

    // 添加错题时的文件选择
    document.getElementById('addQuestionFileInput').addEventListener('change', (e) => {
        const files = Array.from(e.target.files || []);
        files.forEach(f => pendingAddFiles.question.push(f));
        renderAddFilePreviews();
        e.target.value = '';
    });
    document.getElementById('addAnswerFileInput').addEventListener('change', (e) => {
        const files = Array.from(e.target.files || []);
        files.forEach(f => pendingAddFiles.answer.push(f));
        renderAddFilePreviews();
        e.target.value = '';
    });

    // 编辑错题时的文件选择
    document.getElementById('editQuestionFileInput').addEventListener('change', (e) => {
        const files = Array.from(e.target.files || []);
        files.forEach(f => pendingEditFiles.question.push(f));
        renderEditFilePreviews();
        e.target.value = '';
    });
    document.getElementById('editAnswerFileInput').addEventListener('change', (e) => {
        const files = Array.from(e.target.files || []);
        files.forEach(f => pendingEditFiles.answer.push(f));
        renderEditFilePreviews();
        e.target.value = '';
    });

    // 点击附件区激活粘贴目标
    document.querySelectorAll('#addModal .attachment-section').forEach(section => {
        const type = section.dataset.type;
        const target = section.querySelector('.paste-target');
        if (!type || !target) return;
        const activate = () => {
            setActivePasteSection(type);
            target.focus();
        };
        section.addEventListener('click', (e) => {
            if (e.target.closest('input,textarea,button')) return;
            activate();
        });
        target.addEventListener('focus', () => setActivePasteSection(type));
        target.addEventListener('click', activate);
    });
    document.querySelectorAll('#editModal .attachment-section').forEach(section => {
        const type = section.dataset.type;
        if (!type) return;
        const target = section.querySelector('.paste-target');
        if (!target) return;
        const activate = () => {
            setEditActivePasteSection(type);
            target.focus();
        };
        section.addEventListener('click', (e) => {
            if (e.target.closest('input,textarea,button')) return;
            activate();
        });
        target.addEventListener('focus', () => setEditActivePasteSection(type));
        target.addEventListener('click', activate);
    });

    // 全局粘贴事件 - 当添加或编辑错题模态框打开时,粘贴到当前激活区
    document.addEventListener('paste', (e) => {
        if (!e.clipboardData) return;
        const addModal = document.getElementById('addModal');
        const editModal = document.getElementById('editModal');
        if (addModal && addModal.style.display === 'block') {
            if (handlePastedFiles(activePasteType, e.clipboardData)) e.preventDefault();
        } else if (editModal && editModal.style.display === 'block') {
            if (handlePastedEditFiles(editActivePasteType, e.clipboardData)) e.preventDefault();
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

// 添加错题时的待上传文件
const pendingAddFiles = { question: [], answer: [] };

// 标记当前活动的粘贴区
let activePasteType = 'question';

function setActivePasteSection(type) {
    activePasteType = type;
    document.querySelectorAll('#addModal .attachment-section').forEach(s => {
        s.classList.toggle('paste-active', s.dataset.type === type);
    });
}

function handlePastedFiles(type, clipboardData) {
    const items = clipboardData?.items;
    if (!items) return false;
    let added = false;
    for (const item of items) {
        if (item.type && item.type.startsWith('image/')) {
            const blob = item.getAsFile();
            if (!blob) continue;
            const ext = item.type.split('/')[1] || 'png';
            const file = new File([blob], `pasted-${Date.now()}-${pendingAddFiles[type].length}.${ext}`, { type: item.type });
            pendingAddFiles[type].push(file);
            added = true;
        }
    }
    if (added) renderAddFilePreviews();
    return added;
}

function renderAddFilePreviews() {
    renderPendingPreview('addQuestionFileList', pendingAddFiles.question, 'question');
    renderPendingPreview('addAnswerFileList', pendingAddFiles.answer, 'answer');
}

function renderPendingPreview(containerId, files, type) {
    const container = document.getElementById(containerId);
    if (files.length === 0) {
        container.innerHTML = '';
        return;
    }
    container.innerHTML = files.map((f, i) => {
        const url = URL.createObjectURL(f);
        return `
            <div class="file-item pending-file">
                <img src="${url}" alt="${escapeAttr(f.name)}" onclick="window.open('${url}', '_blank')">
                <button class="file-delete" onclick="removePendingAddFile('${type}', ${i})" title="删除">×</button>
                <div class="file-name" title="${escapeAttr(f.name)}">${escapeHtml(f.name)}</div>
            </div>
        `;
    }).join('');
}

function removePendingAddFile(type, index) {
    pendingAddFiles[type].splice(index, 1);
    renderAddFilePreviews();
}

function resetPendingAddFiles() {
    pendingAddFiles.question = [];
    pendingAddFiles.answer = [];
    activePasteType = 'question';
    renderAddFilePreviews();
    document.querySelectorAll('#addModal .attachment-section').forEach(s => s.classList.remove('paste-active'));
}

// 编辑错题时的文件状态
const pendingEditFiles = { question: [], answer: [] };
const existingEditFiles = { question: [], answer: [] };
let editActivePasteType = 'question';

function setEditActivePasteSection(type) {
    editActivePasteType = type;
    document.querySelectorAll('#editModal .attachment-section').forEach(s => {
        if (s.dataset.type) {
            s.classList.toggle('paste-active', s.dataset.type === type);
        }
    });
}

function handlePastedEditFiles(type, clipboardData) {
    const items = clipboardData?.items;
    if (!items) return false;
    let added = false;
    for (const item of items) {
        if (item.type && item.type.startsWith('image/')) {
            const blob = item.getAsFile();
            if (!blob) continue;
            const ext = item.type.split('/')[1] || 'png';
            const file = new File([blob], `pasted-${Date.now()}-${pendingEditFiles[type].length}.${ext}`, { type: item.type });
            pendingEditFiles[type].push(file);
            added = true;
        }
    }
    if (added) renderEditFilePreviews();
    return added;
}

function renderEditFilePreviews() {
    renderEditSection('editQuestionFileList', existingEditFiles.question, pendingEditFiles.question, 'question');
    renderEditSection('editAnswerFileList', existingEditFiles.answer, pendingEditFiles.answer, 'answer');
}

function renderEditSection(containerId, existingFiles, pendingFiles, type) {
    const container = document.getElementById(containerId);
    const existingHtml = existingFiles.map(f => {
        const src = `${API_BASE}/files/${f.id}/content`;
        const name = f.originalName || '';
        return `
            <div class="file-item">
                <img src="${src}" alt="${escapeAttr(name)}" onclick="window.open('${src}', '_blank')">
                <button type="button" class="file-delete" onclick="deleteExistingEditFile(event, ${f.id}, '${type}'); return false;" title="删除">×</button>
                <div class="file-name" title="${escapeAttr(name)}">${escapeHtml(name)}</div>
            </div>
        `;
    }).join('');

    const pendingHtml = pendingFiles.map((file, i) => {
        const url = URL.createObjectURL(file);
        return `
            <div class="file-item pending-file">
                <img src="${url}" alt="${escapeAttr(file.name)}" onclick="window.open('${url}', '_blank')">
                <button type="button" class="file-delete" onclick="removePendingEditFile(event, '${type}', ${i}); return false;" title="删除">×</button>
                <div class="file-name" title="${escapeAttr(file.name)}">${escapeHtml(file.name)}</div>
            </div>
        `;
    }).join('');

    container.innerHTML = existingHtml + pendingHtml;
}

function removePendingEditFile(event, type, index) {
    event.preventDefault();
    event.stopPropagation();
    pendingEditFiles[type].splice(index, 1);
    renderEditFilePreviews();
}

async function deleteExistingEditFile(event, fileId, type) {
    event.preventDefault();
    event.stopPropagation();
    if (!confirm('确定要删除这个文件吗?')) return;
    try {
        await deleteQuestionFile(fileId);
        existingEditFiles[type] = existingEditFiles[type].filter(f => f.id !== fileId);
        renderEditFilePreviews();
    } catch (error) {
        alert('删除失败，请重试');
    }
}

function resetPendingEditFiles() {
    pendingEditFiles.question = [];
    pendingEditFiles.answer = [];
    existingEditFiles.question = [];
    existingEditFiles.answer = [];
    editActivePasteType = 'question';
    renderEditFilePreviews();
    document.querySelectorAll('#editModal .attachment-section').forEach(s => s.classList.remove('paste-active'));
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}

function escapeAttr(s) {
    return escapeHtml(s);
}
