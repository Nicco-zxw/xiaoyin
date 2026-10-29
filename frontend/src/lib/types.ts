export type User={id:string;username:string;createdAt:string};
export type Template={id:string;categoryId:string;title:string;description:string;coverUrl:string;requiredImageCount:number;acceptedMimeTypes:string[];maxFileSizeBytes:number};
export type Stage='MATERIAL_CHECK'|'MOCK_GENERATION'|'RESULT_PACKAGING';
export type Task={id:string;templateId:string;templateTitle:string;status:'QUEUED'|'RUNNING'|'SUCCEEDED'|'FAILED';currentStage:Stage|null;steps:{stage:Stage;status:string;startedAt:string|null;finishedAt:string|null}[];failure:{stage:Stage|null;code:string;message:string}|null;resultUrl:string|null;createdAt:string;startedAt:string|null;finishedAt:string|null};
export type Asset={id:string;previewUrl:string;mimeType:string;sizeBytes:number;width:number;height:number;createdAt:string};
export type Page<T>={items:T[];page:number;size:number;totalPages:number;totalElements:number};
export const stageNames:Record<Stage,string>={MATERIAL_CHECK:'素材检查',MOCK_GENERATION:'模拟生成',RESULT_PACKAGING:'结果整理'};
export const statusNames:Record<Task['status'],string>={QUEUED:'等待处理',RUNNING:'制作中',SUCCEEDED:'制作完成',FAILED:'制作失败'};
