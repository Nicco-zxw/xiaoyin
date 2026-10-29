import type {Metadata} from 'next';
import {Providers} from '@/components/providers';
import {Shell} from '@/components/shell';
import './globals.css';
export const metadata:Metadata={title:'素作 · Material Studio',description:'从一点灵感开始，制作你的创意图片。'};
export default function Layout({children}:{children:React.ReactNode}){return <html lang="zh-CN"><body><Providers><Shell>{children}</Shell></Providers></body></html>;}
